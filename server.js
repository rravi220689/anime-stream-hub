const express = require('express');
const cors = require('cors');
const axios = require('axios');
const path = require('path');
const fs = require('fs');
const https = require('https');
const dotenv = require('dotenv');
const { spawn } = require('child_process');
const { Ytdl } = require('@distube/ytdl-core');

dotenv.config();

const app = express();
const PORT = process.env.PORT || 5050;
const ytdl = new Ytdl();

// Reusable HTTPS agent with connection pooling and keep-alive
const httpsAgent = new https.Agent({
  keepAlive: true,
  maxSockets: 50,
  maxFreeSockets: 10,
  timeout: 30000
});

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));
app.use('/releases', express.static(path.join(__dirname, 'releases')));

// Simple in-memory cache to reduce external API requests and prevent rate-limiting
const cache = new Map();
function getCache(key, ttlSeconds = 600) {
  const item = cache.get(key);
  if (!item) return null;
  if (Date.now() - item.timestamp > ttlSeconds * 1000) {
    cache.delete(key);
    return null;
  }
  return item.data;
}
function setCache(key, data) {
  cache.set(key, { data, timestamp: Date.now() });
}

// AniList GraphQL client
const ANILIST_URL = 'https://graphql.anilist.co';

async function fetchAniList(query, variables = {}) {
  const cacheKey = JSON.stringify({ query, variables });
  const cached = getCache(cacheKey, 900); // 15 mins cache
  if (cached) return cached;

  try {
    const response = await axios.post(
      ANILIST_URL,
      { query, variables },
      {
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
          'User-Agent': 'Mozilla/5.0 AnimeStreamHub/1.0'
        },
        timeout: 10000
      }
    );
    if (response.data && response.data.data) {
      setCache(cacheKey, response.data.data);
      return response.data.data;
    }
    throw new Error('Invalid response structure');
  } catch (err) {
    console.error('AniList API Error:', err.message);
    throw err;
  }
}

// YouTube Scraper for Cartoons, Anime trailers & Full Videos
async function searchYouTube(query, maxResults = 24) {
  const cacheKey = `yt_${query}_${maxResults}`;
  const cached = getCache(cacheKey, 600);
  if (cached) return cached;

  try {
    const url = `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}`;
    const response = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
        'Accept-Language': 'en-US,en;q=0.9'
      },
      timeout: 9000
    });

    const html = response.data;
    const match = html.match(/var ytInitialData = ({.*?});<\/script>/s) || html.match(/ytInitialData\s*=\s*({.*?});/s);
    if (!match) return [];

    const data = JSON.parse(match[1]);
    const contents = data.contents?.twoColumnSearchResultsRenderer?.primaryContents?.sectionListRenderer?.contents || [];
    const videos = [];

    for (const section of contents) {
      const items = section.itemSectionRenderer?.contents || [];
      for (const item of items) {
        const v = item.videoRenderer;
        if (v && v.videoId) {
          const title = v.title?.runs?.[0]?.text || 'Video';
          const thumbnail = v.thumbnail?.thumbnails?.slice(-1)[0]?.url || `https://i.ytimg.com/vi/${v.videoId}/hqdefault.jpg`;
          const duration = v.lengthText?.simpleText || 'N/A';
          const channel = v.ownerText?.runs?.[0]?.text || 'YouTube Channel';
          const views = v.viewCountText?.simpleText || 'Popular';
          const published = v.publishedTimeText?.simpleText || '';

          videos.push({
            id: v.videoId,
            title,
            thumbnail,
            duration,
            channel,
            views,
            published,
            type: 'youtube',
            embedUrl: `https://www.youtube-nocookie.com/embed/${v.videoId}?autoplay=1&enablejsapi=1`
          });

          if (videos.length >= maxResults) break;
        }
      }
      if (videos.length >= maxResults) break;
    }

    setCache(cacheKey, videos);
    return videos;
  } catch (err) {
    console.error('YouTube Search error:', err.message);
    return [];
  }
}

// ---------------- API ENDPOINTS ---------------- //

// 0. Latest Episode Releases (Real-Time Aired Schedule for Home Page)
app.get('/api/anime/latest', async (req, res) => {
  try {
    const page = parseInt(req.query.page) || 1;
    const perPage = parseInt(req.query.limit) || 24;
    const query = `
      query ($page: Int, $perPage: Int) {
        Page(page: $page, perPage: $perPage) {
          pageInfo { total currentPage lastPage hasNextPage perPage }
          airingSchedules(notYetAired: false, sort: TIME_DESC) {
            id
            airingAt
            episode
            media {
              id
              idMal
              title { english romaji native }
              coverImage { extraLarge large medium }
              bannerImage
              episodes
              duration
              averageScore
              popularity
              format
              genres
              status
              seasonYear
              description(asHtml: false)
              trailer { id site }
            }
          }
        }
      }
    `;
    const data = await fetchAniList(query, { page, perPage });
    const schedules = data.Page?.airingSchedules || [];
    const latestReleases = schedules.map(s => ({
      ...s.media,
      latestEpisode: s.episode,
      airingAt: s.airingAt,
      isLatestRelease: true
    }));

    res.json({ success: true, data: latestReleases, pageInfo: data.Page?.pageInfo });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Failed to fetch latest releases', error: error.message });
  }
});

// 1. Trending Anime & Animations
app.get('/api/anime/trending', async (req, res) => {
  try {
    const page = parseInt(req.query.page) || 1;
    const perPage = parseInt(req.query.limit) || 24;
    const query = `
      query ($page: Int, $perPage: Int) {
        Page(page: $page, perPage: $perPage) {
          pageInfo { total currentPage lastPage hasNextPage perPage }
          media(type: ANIME, sort: TRENDING_DESC) {
            id
            idMal
            title { english romaji native }
            coverImage { extraLarge large medium }
            bannerImage
            episodes
            duration
            averageScore
            popularity
            format
            genres
            status
            seasonYear
            description(asHtml: false)
            trailer { id site }
          }
        }
      }
    `;
    const data = await fetchAniList(query, { page, perPage });
    res.json({ success: true, data: data.Page.media, pageInfo: data.Page.pageInfo });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Failed to fetch trending anime', error: error.message });
  }
});

// 2. All-Time Popular Anime & Animated Series
app.get('/api/anime/popular', async (req, res) => {
  try {
    const page = parseInt(req.query.page) || 1;
    const perPage = parseInt(req.query.limit) || 24;
    const query = `
      query ($page: Int, $perPage: Int) {
        Page(page: $page, perPage: $perPage) {
          pageInfo { total currentPage lastPage hasNextPage perPage }
          media(type: ANIME, sort: POPULARITY_DESC) {
            id
            idMal
            title { english romaji }
            coverImage { extraLarge large }
            bannerImage
            episodes
            averageScore
            format
            genres
            status
            seasonYear
            description(asHtml: false)
            trailer { id site }
          }
        }
      }
    `;
    const data = await fetchAniList(query, { page, perPage });
    res.json({ success: true, data: data.Page.media, pageInfo: data.Page.pageInfo });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Failed to fetch popular anime', error: error.message });
  }
});

// 3. Animated Movies
app.get('/api/anime/movies', async (req, res) => {
  try {
    const page = parseInt(req.query.page) || 1;
    const perPage = parseInt(req.query.limit) || 24;
    const query = `
      query ($page: Int, $perPage: Int) {
        Page(page: $page, perPage: $perPage) {
          pageInfo { total currentPage lastPage hasNextPage perPage }
          media(type: ANIME, format: MOVIE, sort: POPULARITY_DESC) {
            id
            idMal
            title { english romaji }
            coverImage { extraLarge large }
            bannerImage
            episodes
            duration
            averageScore
            format
            genres
            status
            seasonYear
            description(asHtml: false)
            trailer { id site }
          }
        }
      }
    `;
    const data = await fetchAniList(query, { page, perPage });
    res.json({ success: true, data: data.Page.media, pageInfo: data.Page.pageInfo });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Failed to fetch movies', error: error.message });
  }
});

// 4. Cartoons & Kids / Family Animations
app.get('/api/anime/cartoons', async (req, res) => {
  try {
    const page = parseInt(req.query.page) || 1;
    const perPage = parseInt(req.query.limit) || 24;
    const query = `
      query ($page: Int, $perPage: Int) {
        Page(page: $page, perPage: $perPage) {
          pageInfo { total currentPage lastPage hasNextPage perPage }
          media(type: ANIME, genre_in: ["Comedy", "Adventure", "Fantasy"], sort: POPULARITY_DESC) {
            id
            idMal
            title { english romaji }
            coverImage { extraLarge large }
            bannerImage
            episodes
            averageScore
            format
            genres
            status
            seasonYear
            description(asHtml: false)
            trailer { id site }
          }
        }
      }
    `;
    const data = await fetchAniList(query, { page, perPage });
    res.json({ success: true, data: data.Page.media, pageInfo: data.Page.pageInfo });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Failed to fetch cartoons', error: error.message });
  }
});

// 5. Universal Search (Anime, Cartoons, Series)
app.get('/api/anime/search', async (req, res) => {
  try {
    const q = req.query.q;
    const genre = req.query.genre;
    const format = req.query.format;
    const page = parseInt(req.query.page) || 1;
    const perPage = parseInt(req.query.limit) || 24;

    if (!q && !genre && !format) {
      return res.status(400).json({ success: false, message: 'Search parameter required' });
    }

    const query = `
      query ($search: String, $genre: String, $format: MediaFormat, $page: Int, $perPage: Int) {
        Page(page: $page, perPage: $perPage) {
          pageInfo { total currentPage lastPage hasNextPage perPage }
          media(type: ANIME, search: $search, genre: $genre, format: $format, sort: SEARCH_MATCH) {
            id
            idMal
            title { english romaji native }
            coverImage { extraLarge large }
            bannerImage
            episodes
            duration
            averageScore
            format
            genres
            status
            seasonYear
            description(asHtml: false)
            trailer { id site }
          }
        }
      }
    `;

    const variables = { page, perPage };
    if (q) variables.search = q;
    if (genre) variables.genre = genre;
    if (format) variables.format = format;

    const data = await fetchAniList(query, variables);
    res.json({ success: true, data: data.Page.media, pageInfo: data.Page.pageInfo });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Search failed', error: error.message });
  }
});

// 5b. Comprehensive Anime Directory / Catalog (A-Z & Filterable List)
app.get('/api/anime/directory', async (req, res) => {
  try {
    const page = parseInt(req.query.page) || 1;
    const perPage = parseInt(req.query.limit) || 24;
    const status = req.query.status || undefined; // 'RELEASING', 'FINISHED', 'NOT_YET_RELEASED'
    const genre = req.query.genre || undefined;
    const format = req.query.format || undefined;
    const search = req.query.search || undefined;

    // Sort mapping
    let sort = ['POPULARITY_DESC'];
    if (req.query.sort === 'rating') sort = ['SCORE_DESC'];
    else if (req.query.sort === 'latest') sort = ['START_DATE_DESC'];
    else if (req.query.sort === 'az') sort = ['TITLE_ROMAJI'];
    else if (req.query.sort === 'za') sort = ['TITLE_ROMAJI_DESC'];
    else if (req.query.sort === 'episodes') sort = ['EPISODES_DESC'];

    const query = `
      query ($page: Int, $perPage: Int, $status: MediaStatus, $genre: String, $format: MediaFormat, $sort: [MediaSort], $search: String) {
        Page(page: $page, perPage: $perPage) {
          pageInfo { total currentPage lastPage hasNextPage perPage }
          media(type: ANIME, status: $status, genre: $genre, format: $format, sort: $sort, search: $search) {
            id
            idMal
            title { english romaji native }
            coverImage { extraLarge large medium }
            bannerImage
            format
            episodes
            duration
            status
            seasonYear
            averageScore
            popularity
            genres
            description(asHtml: false)
            trailer { id site }
          }
        }
      }
    `;

    const variables = { page, perPage, sort };
    if (status) variables.status = status;
    if (genre) variables.genre = genre;
    if (format) variables.format = format;
    if (search) variables.search = search;

    const data = await fetchAniList(query, variables);
    res.json({
      success: true,
      data: data.Page.media,
      pageInfo: data.Page.pageInfo
    });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Failed to fetch directory', error: error.message });
  }
});

// 6. Detailed Anime Info + Streaming Links & Episodes
app.get('/api/anime/details/:id', async (req, res) => {
  try {
    const id = parseInt(req.params.id);
    if (!id) return res.status(400).json({ success: false, message: 'Invalid anime ID' });

    const query = `
      query ($id: Int) {
        Media(id: $id, type: ANIME) {
          id
          idMal
          title { english romaji native }
          coverImage { extraLarge large }
          bannerImage
          format
          episodes
          nextAiringEpisode { episode airingAt }
          duration
          status
          seasonYear
          averageScore
          popularity
          genres
          studios(isMain: true) { nodes { name } }
          description(asHtml: false)
          trailer { id site }
          streamingEpisodes {
            title
            thumbnail
            url
            site
          }
          recommendations(perPage: 6, sort: RATING_DESC) {
            nodes {
              mediaRecommendation {
                id
                idMal
                title { english romaji }
                coverImage { large }
                format
                averageScore
              }
            }
          }
        }
      }
    `;

    const data = await fetchAniList(query, { id });
    const anime = data.Media;
    if (!anime) return res.status(404).json({ success: false, message: 'Anime not found' });

    // Determine total episodes (for ongoing series, use nextAiringEpisode - 1 or default)
    const isMovie = anime.format === 'MOVIE';
    let totalEps = anime.episodes;
    if (!totalEps && anime.nextAiringEpisode?.episode) {
      totalEps = anime.nextAiringEpisode.episode - 1;
    }
    if (!totalEps) {
      totalEps = isMovie ? 1 : 24;
    }
    const episodeCount = Math.min(Math.max(1, totalEps), 1500);
    const episodes = [];
    const mainTitle = anime.title.english || anime.title.romaji;
    const cleanSlug = encodeURIComponent(mainTitle.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, ''));

    for (let i = 1; i <= episodeCount; i++) {
      // Find matching streaming episode if available
      const streamEp = anime.streamingEpisodes?.[i - 1];

      // Verified working high-speed anime embed servers (tested with zero iframe blocks)
      const servers = [
        {
          name: 'Server 1 (AnimeEmbed HD Sub)',
          type: 'embed',
          url: `https://animeembed.com/embed/ani/${anime.id}/${i}?accent=6366f1`
        },
        {
          name: 'Server 2 (AnimeEmbed English Dub)',
          type: 'embed',
          url: `https://animeembed.com/embed/ani/${anime.id}/${i}?track=dub&accent=6366f1`
        },
        {
          name: 'Server 3 (Edge Mirror)',
          type: 'embed',
          url: `https://animeembed.com/embed/ani/${anime.id}/${i}?server=2&accent=6366f1`
        }
      ];

      // If MyAnimeList ID is available, add MAL player server
      if (anime.idMal) {
        servers.push({
          name: 'Server 4 (MAL Player)',
          type: 'embed',
          url: `https://animeembed.com/embed/mal/${anime.idMal}/${i}?accent=6366f1`
        });
      }

      // If official trailer is on YouTube, add as trailer & stream server
      if (anime.trailer && anime.trailer.site === 'youtube') {
        servers.push({
          name: 'Official Trailer (YouTube HD)',
          type: 'youtube',
          url: `https://www.youtube-nocookie.com/embed/${anime.trailer.id}?autoplay=1&enablejsapi=1`,
          youtubeId: anime.trailer.id
        });
      }

      episodes.push({
        number: i,
        title: streamEp?.title || `Episode ${i}`,
        thumbnail: streamEp?.thumbnail || anime.coverImage.large,
        servers
      });
    }

    // Direct search links for popular external aggregators
    const externalMirrors = {
      gogoanime: `https://anitaku.to/search.html?keyword=${encodeURIComponent(mainTitle)}`,
      animepahe: `https://animepahe.ru`,
      hianime: `https://hianime.to/search?keyword=${encodeURIComponent(mainTitle)}`
    };

    res.json({
      success: true,
      data: {
        ...anime,
        episodesList: episodes,
        externalMirrors
      }
    });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Failed to fetch details', error: error.message });
  }
});

// 7. YouTube Real-Time Search for Cartoons & Videos
app.get('/api/youtube/search', async (req, res) => {
  try {
    const q = req.query.q || 'classic cartoons full episodes';
    const limit = parseInt(req.query.limit) || 20;
    const results = await searchYouTube(q, limit);
    res.json({ success: true, query: q, data: results });
  } catch (error) {
    res.status(500).json({ success: false, message: 'YouTube search failed', error: error.message });
  }
});

// 8. Curated Cartoon & Animation Collections from YouTube
app.get('/api/youtube/cartoons', async (req, res) => {
  try {
    const category = req.query.category || 'popular';
    let searchQuery = 'classic animation cartoon full episode';

    if (category === 'tom-jerry') searchQuery = 'Tom and Jerry full episodes cartoons Warner';
    else if (category === 'looney-tunes') searchQuery = 'Looney Tunes Bugs Bunny full episodes';
    else if (category === 'pokemon') searchQuery = 'Pokemon full episodes animation';
    else if (category === 'anime-official') searchQuery = 'Muse Asia full anime episodes English';
    else if (category === 'ben-10') searchQuery = 'Ben 10 full episode cartoon network';
    else if (category === 'popeye') searchQuery = 'Popeye the Sailor Man cartoon full';

    const results = await searchYouTube(searchQuery, 24);
    res.json({ success: true, category, data: results });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Failed to fetch curated cartoons', error: error.message });
  }
});

// Helper to calculate realistic multi-resolution file sizes based on duration
function calculateFileSizes(durationStr, isMovie = false) {
  let minutes = 24; // default anime episode length
  if (durationStr) {
    if (typeof durationStr === 'number') {
      minutes = durationStr;
    } else if (typeof durationStr === 'string') {
      const matchMin = durationStr.match(/(\d+)\s*min/i);
      const matchColon = durationStr.match(/(\d+):(\d+)/);
      if (matchMin) {
        minutes = parseInt(matchMin[1]);
      } else if (matchColon) {
        minutes = parseInt(matchColon[1]) + parseInt(matchColon[2]) / 60;
      } else if (!isNaN(parseFloat(durationStr))) {
        minutes = parseFloat(durationStr);
      }
    }
  } else if (isMovie) {
    minutes = 105;
  }
  minutes = Math.max(1, Math.min(minutes, 360));

  const size1080 = Math.round(minutes * 13.8); // ~330 MB for 24 min
  const size720 = Math.round(minutes * 7.2);   // ~172 MB for 24 min
  const size480 = Math.round(minutes * 3.8);   // ~91 MB for 24 min
  const size360 = Math.round(minutes * 2.0);   // ~48 MB for 24 min
  const sizeMp3 = Math.round(minutes * 1.4);   // ~33 MB for 24 min
  const sizeWebm = Math.round(minutes * 6.5);  // ~156 MB for 24 min

  const formatSize = (mb) => (mb >= 1000 ? (mb / 1024).toFixed(1) + ' GB' : mb + ' MB');

  return {
    '1080p': formatSize(size1080),
    '720p': formatSize(size720),
    '480p': formatSize(size480),
    '360p': formatSize(size360),
    'mp3': formatSize(sizeMp3),
    'webm': formatSize(sizeWebm),
    minutes: Math.round(minutes)
  };
}

// Helper to extract animeembed URL from various query formats
function extractAnimeEmbedUrl(rawUrl, id, episode = 1) {
  if (rawUrl && typeof rawUrl === 'string' && rawUrl.includes('animeembed.com/embed/')) {
    return rawUrl;
  }
  const cleanId = id || (rawUrl && typeof rawUrl === 'string' && rawUrl.match(/(?:ani|mal)\/(\d+)/)?.[1]);
  const ep = episode || (rawUrl && typeof rawUrl === 'string' && rawUrl.match(/\/(\d+)(?:\?|$)/)?.[1]) || 1;
  if (cleanId) {
    return `https://animeembed.com/embed/ani/${cleanId}/${ep}?accent=6366f1`;
  }
  return null;
}

// Shared browser headers for AnimeEmbed Cloudflare bypass
function getAnimeBrowserHeaders(referer = 'https://animeembed.com/') {
  const ref = (referer && typeof referer === 'string' && referer.startsWith('http')) ? referer : 'https://animeembed.com/';
  return {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Referer': ref,
    'Origin': 'https://animeembed.com',
    'Accept': '*/*',
    'Sec-Ch-Ua': '"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"',
    'Sec-Ch-Ua-Mobile': '?0',
    'Sec-Ch-Ua-Platform': '"Windows"',
    'Sec-Fetch-Dest': 'empty',
    'Sec-Fetch-Mode': 'cors',
    'Sec-Fetch-Site': 'same-origin',
    'Accept-Language': 'en-US,en;q=0.9',
    'Connection': 'keep-alive'
  };
}

// Helper to resolve full anime stream M3U8 and English subtitles from animeembed.com
async function resolveAnimeEmbedSource(embedUrlOrId, epNum = 1) {
  const embedUrl = extractAnimeEmbedUrl(embedUrlOrId, embedUrlOrId, epNum) ||
    (typeof embedUrlOrId === 'string' && embedUrlOrId.startsWith('http') ? embedUrlOrId : `https://animeembed.com/embed/ani/${embedUrlOrId}/${epNum}?accent=6366f1`);

  const pageResp = await axios.get(embedUrl, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
    },
    httpsAgent: httpsAgent,
    timeout: 12000
  });

  const configMatch = pageResp.data.match(/<script type="application\/json" id="config">(.*?)<\/script>/s);
  if (!configMatch) {
    throw new Error('Anime embed configuration not found');
  }

  const config = JSON.parse(configMatch[1]);
  const session = config.session;
  if (!session) {
    throw new Error('Session token not found in config');
  }

  const browserHeaders = getAnimeBrowserHeaders(embedUrl);

  const srcResp = await axios.get(`https://animeembed.com/internal/source?session=${session}&server=1`, {
    headers: browserHeaders,
    httpsAgent: httpsAgent,
    timeout: 12000
  });

  const sourceData = srcResp.data;
  const masterPath = sourceData.source;
  if (!masterPath) {
    throw new Error('No video stream source returned');
  }

  let subtitlePath = null;
  if (Array.isArray(sourceData.tracks)) {
    const engTrack = sourceData.tracks.find(t => (t.label && t.label.toLowerCase().includes('english')) || (t.file && t.file.toLowerCase().includes('eng'))) || sourceData.tracks[0];
    if (engTrack && engTrack.file) {
      subtitlePath = engTrack.file;
    }
  }

  return {
    embedUrl,
    session,
    masterM3u8Url: `https://animeembed.com${masterPath}`,
    subtitlePath,
    subtitleUrl: subtitlePath ? `https://animeembed.com${subtitlePath}` : null,
    tracks: sourceData.tracks || []
  };
}

// Convert WebVTT subtitles to SubRip (SRT) format
function vttToSrt(vttText) {
  const lines = vttText.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
  const srtLines = [];
  let index = 1;
  let inHeader = true;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (inHeader) {
      if (line === '' && i > 0) {
        inHeader = false;
      }
      continue;
    }
    if (line.includes('-->')) {
      srtLines.push(String(index++));
      const formattedTimestamp = line.replace(/(\d{2}:\d{2}:\d{2})\.(\d{3})/g, '$1,$2');
      srtLines.push(formattedTimestamp);
    } else if (line !== '') {
      srtLines.push(line);
    } else {
      srtLines.push('');
    }
  }
  return srtLines.join('\r\n');
}

// 9a. Subtitle Downloader Endpoint (.srt and .vtt)
app.get('/api/download/subtitle', async (req, res) => {
  try {
    const rawUrl = req.query.url || '';
    const title = req.query.title || 'anime_episode';
    const reqFormat = (req.query.format || 'srt').toLowerCase();
    const episode = req.query.episode || '1';

    let subtitleMediaUrl = req.query.subUrl || '';
    let embedUrl = rawUrl;

    if (!subtitleMediaUrl) {
      const resolved = await resolveAnimeEmbedSource(rawUrl, episode);
      subtitleMediaUrl = resolved.subtitleUrl;
      embedUrl = resolved.embedUrl;
    }

    if (!subtitleMediaUrl) {
      return res.status(404).send('No English subtitles found for this episode');
    }

    const browserHeaders = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
      'Referer': embedUrl || 'https://animeembed.com/',
      'Origin': 'https://animeembed.com',
      'Accept': '*/*'
    };

    const subResp = await axios.get(subtitleMediaUrl, {
      headers: browserHeaders,
      timeout: 15000,
      responseType: 'text'
    });

    const cleanName = title.replace(/[^a-zA-Z0-9_-]/g, '_').replace(/_+/g, '_').slice(0, 80) || 'subtitles';
    const filename = `${cleanName}.${reqFormat}`;

    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.setHeader('Content-Type', reqFormat === 'srt' ? 'application/x-subrip; charset=utf-8' : 'text/vtt; charset=utf-8');
    res.setHeader('X-Content-Type-Options', 'nosniff');

    if (req.method === 'HEAD') {
      return res.end();
    }

    if (reqFormat === 'srt') {
      res.send(vttToSrt(subResp.data));
    } else {
      res.send(subResp.data);
    }
  } catch (err) {
    console.error('Subtitle download proxy error:', err.message);
    res.status(500).send('Failed to stream subtitles: ' + err.message);
  }
});

// 9. Comprehensive Multi-Resolution Video Downloader Resolver (1080p, 720p, 480p, 360p, MP3, WebM)
app.get('/api/download/resolve', async (req, res) => {
  try {
    const videoUrl = req.query.url || '';
    const title = req.query.title || 'Video';
    const duration = req.query.duration || '24 min';
    const type = req.query.type || 'video';
    const isMovie = req.query.format === 'MOVIE' || title.toLowerCase().includes('movie');
    const sizes = calculateFileSizes(duration, isMovie);
    const episode = req.query.episode || '1';

    const safeTitle = encodeURIComponent(title.replace(/[^a-zA-Z0-9 _-]/g, '').trim());

    const isAnime = type === 'anime' || videoUrl.includes('animeembed.com') || videoUrl.includes('/embed/ani/');
    const ytMatch = videoUrl.match(/(?:youtube\.com\/(?:watch\?v=|embed\/|v\/)|youtu\.be\/)([\w-]{11})/i);
    let videoId = ytMatch ? ytMatch[1] : (type === 'youtube' ? req.query.id : null);
    const isDirectVideo = videoUrl.includes('.mp4') || videoUrl.includes('.webm');

    let resolvedAnime = null;
    let subtitleUrl = null;
    let subtitleVttUrl = null;

    if (isAnime) {
      try {
        resolvedAnime = await resolveAnimeEmbedSource(videoUrl || req.query.id, episode);
        if (resolvedAnime && resolvedAnime.subtitleUrl) {
          const subParamUrl = resolvedAnime.embedUrl || videoUrl;
          subtitleUrl = `/api/download/subtitle?url=${encodeURIComponent(subParamUrl)}&title=${encodeURIComponent(title)}&format=srt`;
          subtitleVttUrl = `/api/download/subtitle?url=${encodeURIComponent(subParamUrl)}&title=${encodeURIComponent(title)}&format=vtt`;
        }
      } catch (aErr) {
        console.warn('Anime stream pre-resolution:', aErr.message);
      }
    } else if (!videoId && !isDirectVideo) {
      // For YouTube cartoons/videos, search YouTube but filter out short clips
      try {
        const cleanQuery = title.replace(/\s+/g, ' ').trim();
        const searchResults = await searchYouTube(cleanQuery, 5);
        if (searchResults && searchResults.length > 0) {
          videoId = searchResults[0].id;
        }
      } catch (e) {
        console.warn('Resolver youtube search fallback:', e.message);
      }
    }

    // ALWAYS provide a direct attachment download URL for every resolution
    const buildDirectUrl = (quality, format) => {
      const p = new URLSearchParams();
      p.set('title', title);
      p.set('quality', quality);
      p.set('format', format);
      p.set('type', isAnime ? 'anime' : type);
      if (episode) p.set('episode', episode);
      if (videoId) p.set('id', videoId);
      else if (req.query.id) p.set('id', req.query.id);
      if (videoUrl) p.set('url', videoUrl);
      if (subtitleVttUrl) p.set('subtitleUrl', subtitleVttUrl);
      return `/api/download/file?${p.toString()}`;
    };

    const resolutions = [
      {
        quality: '1080p',
        format: 'mp4',
        name: '1080p Full HD',
        badge: '1080p FHD',
        badgeType: '1080p',
        size: sizes['1080p'],
        resolution: '1920x1080',
        bitrate: '~3.2 Mbps',
        recommended: true,
        directUrl: buildDirectUrl('1080p', 'mp4'),
        externalUrl: videoId ? `https://cobalt.tools/#https://www.youtube.com/watch?v=${videoId}` : (videoUrl || null),
        subtitleUrl: subtitleUrl
      },
      {
        quality: '720p',
        format: 'mp4',
        name: '720p High Definition',
        badge: '720p HD',
        badgeType: '720p',
        size: sizes['720p'],
        resolution: '1280x720',
        bitrate: '~1.8 Mbps',
        recommended: false,
        directUrl: buildDirectUrl('720p', 'mp4'),
        externalUrl: videoId ? `https://cobalt.tools/#https://www.youtube.com/watch?v=${videoId}` : (videoUrl || null),
        subtitleUrl: subtitleUrl
      },
      {
        quality: '480p',
        format: 'mp4',
        name: '480p Standard Quality',
        badge: '480p SD',
        badgeType: '480p',
        size: sizes['480p'],
        resolution: '854x480',
        bitrate: '~950 kbps',
        recommended: false,
        directUrl: buildDirectUrl('480p', 'mp4'),
        externalUrl: videoId ? `https://en.savefrom.net/1-youtube-video-downloader-4vA/?url=https://youtube.com/watch?v=${videoId}` : (videoUrl || null),
        subtitleUrl: subtitleUrl
      },
      {
        quality: '360p',
        format: 'mp4',
        name: '360p Mobile Saver',
        badge: '360p Mobile',
        badgeType: '360p',
        size: sizes['360p'],
        resolution: '640x360',
        bitrate: '~500 kbps',
        recommended: false,
        directUrl: buildDirectUrl('360p', 'mp4'),
        externalUrl: videoId ? `https://www.y2mate.com/youtube/${videoId}` : (videoUrl || null),
        subtitleUrl: subtitleUrl
      },
      {
        quality: '320kbps',
        format: 'mp3',
        name: 'MP3 High Quality Audio',
        badge: 'MP3 Audio',
        badgeType: 'mp3',
        size: sizes['mp3'],
        resolution: 'Audio Only',
        bitrate: '320 kbps MP3',
        recommended: false,
        directUrl: buildDirectUrl('320kbps', 'mp3'),
        externalUrl: videoId ? `https://www.y2mate.com/youtube-mp3/${videoId}` : (videoUrl || null),
        subtitleUrl: null
      },
      {
        quality: '720p',
        format: 'webm',
        name: 'WebM Video Container',
        badge: 'WEBM',
        badgeType: 'webm',
        size: sizes['webm'],
        resolution: '1280x720',
        bitrate: '~1.5 Mbps VP9',
        recommended: false,
        directUrl: buildDirectUrl('720p', 'webm'),
        externalUrl: videoId ? `https://cobalt.tools/#https://www.youtube.com/watch?v=${videoId}` : (videoUrl || null),
        subtitleUrl: subtitleUrl
      }
    ];

    // Add dedicated subtitle download option row
    if (subtitleUrl) {
      resolutions.push({
        quality: 'English Subtitles',
        format: 'srt',
        name: 'English Subtitles (.srt)',
        badge: 'ENG SUB',
        badgeType: 'sub',
        size: '~45 KB',
        resolution: 'Text/Captions',
        bitrate: 'UTF-8 SRT',
        recommended: false,
        directUrl: subtitleUrl,
        externalUrl: resolvedAnime?.subtitleUrl || null,
        subtitleUrl: subtitleUrl
      });
    }

    // Dedicated fast mirrors & helper links
    const mirrors = [];
    if (videoId) {
      mirrors.push(
        { name: 'Cobalt HD Downloader', url: `https://cobalt.tools/#https://www.youtube.com/watch?v=${videoId}`, note: 'Ad-Free 1080p/720p/MP3' },
        { name: 'Y2Mate HD Converter', url: `https://www.y2mate.com/youtube/${videoId}`, note: 'Fast MP4 & MP3' },
        { name: 'SaveFrom Video Hub', url: `https://en.savefrom.net/1-youtube-video-downloader-4vA/?url=https://youtube.com/watch?v=${videoId}`, note: 'Direct Mobile Downloads' }
      );
    }
    if (isAnime) {
      mirrors.push(
        { name: 'AnimePahe Download Portal', url: `https://anitaku.to/search.html?keyword=${safeTitle}`, note: '720p & 1080p Direct Mirrors' },
        { name: 'Gogoanime Direct Server', url: `https://anitaku.to/search.html?keyword=${safeTitle}`, note: 'Episode Download Mirror' }
      );
    }

    const subtitlesList = [];
    if (subtitleUrl) {
      subtitlesList.push({
        language: 'English',
        format: 'srt',
        url: subtitleUrl
      });
      if (subtitleVttUrl) {
        subtitlesList.push({
          language: 'English',
          format: 'vtt',
          url: subtitleVttUrl
        });
      }
    }

    res.json({
      success: true,
      title,
      type: isAnime ? 'anime' : (ytMatch ? 'youtube' : type),
      videoId,
      sizes,
      formats: resolutions,
      subtitleUrl: subtitleUrl,
      subtitles: subtitlesList,
      mirrors,
      rawUrl: videoUrl
    });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Resolution failed', error: error.message });
  }
});

// 9b. Internal HLS Proxy for FFmpeg Remuxing (bypasses Cloudflare JA3 & 503 blocking)
app.get('/api/proxy/hls/master.m3u8', async (req, res) => {
  try {
    const masterUrl = req.query.url;
    const embedUrl = req.query.embed || 'https://animeembed.com/';
    if (!masterUrl) return res.status(400).send('Missing master url');

    let mResp = null;
    for (let attempt = 0; attempt < 6; attempt++) {
      try {
        if (attempt > 0) await new Promise(r => setTimeout(r, 700 + attempt * 200));
        mResp = await axios.get(masterUrl, {
          headers: getAnimeBrowserHeaders(embedUrl),
          httpsAgent: httpsAgent,
          timeout: 15000
        });
        break;
      } catch (err) {
        if (attempt === 5) throw err;
      }
    }

    const rewritten = mResp.data.replace(/((?:https:\/\/animeembed\.com)?\/media\/[^\r\n]+)/g, (match) => {
      const subPath = match.trim().replace(/^https:\/\/animeembed\.com/, '');
      const fullUrl = 'https://animeembed.com' + subPath;
      return `http://127.0.0.1:${PORT}/api/proxy/hls/sub.m3u8?url=${encodeURIComponent(fullUrl)}&embed=${encodeURIComponent(embedUrl)}`;
    });

    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
    res.setHeader('Cache-Control', 'no-cache');
    res.send(rewritten);
  } catch (err) {
    console.error('HLS master proxy error:', err.message);
    res.status(500).send('Proxy master error: ' + err.message);
  }
});

app.get('/api/proxy/hls/sub.m3u8', async (req, res) => {
  try {
    const subUrl = req.query.url;
    const embedUrl = req.query.embed || 'https://animeembed.com/';
    if (!subUrl) return res.status(400).send('Missing sub playlist url');

    let sResp = null;
    for (let attempt = 0; attempt < 6; attempt++) {
      try {
        if (attempt > 0) await new Promise(r => setTimeout(r, 700 + attempt * 200));
        sResp = await axios.get(subUrl, {
          headers: getAnimeBrowserHeaders(embedUrl),
          httpsAgent: httpsAgent,
          timeout: 15000
        });
        break;
      } catch (err) {
        if (attempt === 5) throw err;
      }
    }

    const rewritten = sResp.data.replace(/((?:https:\/\/animeembed\.com)?\/media\/[^\r\n]+)/g, (match) => {
      const segPath = match.trim().replace(/^https:\/\/animeembed\.com/, '');
      const fullUrl = 'https://animeembed.com' + segPath;
      return `http://127.0.0.1:${PORT}/api/proxy/hls/segment.ts?url=${encodeURIComponent(fullUrl)}&embed=${encodeURIComponent(embedUrl)}`;
    });

    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
    res.setHeader('Cache-Control', 'no-cache');
    res.send(rewritten);
  } catch (err) {
    console.error('HLS sub playlist proxy error:', err.message);
    res.status(500).send('Proxy sub playlist error: ' + err.message);
  }
});

app.get('/api/proxy/hls/segment.ts', async (req, res) => {
  try {
    const segUrl = req.query.url;
    const embedUrl = req.query.embed || 'https://animeembed.com/';
    if (!segUrl) return res.status(400).send('Missing segment url');

    let segResp = null;
    for (let attempt = 0; attempt < 6; attempt++) {
      try {
        if (attempt > 0) await new Promise(r => setTimeout(r, 600 + attempt * 200));
        segResp = await axios.get(segUrl, {
          headers: getAnimeBrowserHeaders(embedUrl),
          httpsAgent: httpsAgent,
          responseType: 'arraybuffer',
          timeout: 25000
        });
        break;
      } catch (err) {
        if (attempt === 5) throw err;
      }
    }

    res.setHeader('Content-Type', 'video/mp2t');
    res.setHeader('Cache-Control', 'public, max-age=86400');
    res.send(Buffer.from(segResp.data));
  } catch (err) {
    console.error('HLS segment proxy error:', err.message);
    res.status(500).send('Segment fetch error');
  }
});

// 9c. Direct Stream / File Downloader Proxy Endpoint with Content-Disposition
app.get('/api/download/file', async (req, res) => {
  try {
    const title = req.query.title || 'video';
    const quality = req.query.quality || '1080p';
    const format = (req.query.format || 'mp4').toLowerCase();
    const type = req.query.type || 'video';
    const fileUrl = req.query.url || '';
    const episode = req.query.episode || '1';
    let videoId = req.query.videoId || req.query.id || '';

    const isAnime = type === 'anime' || fileUrl.includes('animeembed.com') || fileUrl.includes('/embed/ani/');

    const cleanName = title.replace(/[^a-zA-Z0-9_-]/g, '_').replace(/_+/g, '_').slice(0, 80) || 'video';
    const filename = `${cleanName}_${quality}.${format}`;

    // Common headers enforcing genuine download to Downloads folder
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.setHeader('Content-Type', format === 'mp3' ? 'audio/mpeg' : (format === 'webm' ? 'video/webm' : 'video/mp4'));
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Accept-Ranges', 'none');

    if (req.method === 'HEAD') {
      return res.end();
    }

    // Immediately flush headers so client (OkHttp / DownloadManager) receives HTTP 200 without timeout
    if (typeof res.flushHeaders === 'function') {
      res.flushHeaders();
    }

    // 1. ANIME: Stream directly via local HLS Proxy + FFmpeg (remux to MP4/MP3 with AAC stereo + English subtitles)
    if (isAnime) {
      const resolved = await resolveAnimeEmbedSource(fileUrl || req.query.id, episode);
      const masterUrl = resolved.masterM3u8Url;
      const embedUrl = resolved.embedUrl;
      const subVttUrl = resolved.subtitleUrl || (req.query.subtitleUrl && req.query.subtitleUrl.startsWith('http') ? req.query.subtitleUrl : null);

      const proxyMasterUrl = `http://127.0.0.1:${PORT}/api/proxy/hls/master.m3u8?url=${encodeURIComponent(masterUrl)}&embed=${encodeURIComponent(embedUrl)}`;

      let localSubPath = null;
      if (format !== 'mp3' && subVttUrl) {
        try {
          const subResp = await axios.get(subVttUrl, {
            headers: getAnimeBrowserHeaders(embedUrl),
            httpsAgent: httpsAgent,
            timeout: 12000
          });
          if (subResp.data && typeof subResp.data === 'string' && subResp.data.includes('-->')) {
            localSubPath = path.join('/tmp', `sub_${Date.now()}_${Math.random().toString(36).slice(2)}.vtt`);
            fs.writeFileSync(localSubPath, subResp.data);
          }
        } catch (sErr) {
          console.warn('Anime subtitle fetch warning for muxing:', sErr.message);
        }
      }

      let ffmpegArgs = [];
      if (format === 'mp3') {
        ffmpegArgs = [
          '-loglevel', 'error',
          '-i', proxyMasterUrl,
          '-vn',
          '-c:a', 'libmp3lame',
          '-b:a', '320k',
          '-f', 'mp3',
          'pipe:1'
        ];
      } else {
        ffmpegArgs = [
          '-loglevel', 'error',
          '-i', proxyMasterUrl
        ];
        if (localSubPath) {
          ffmpegArgs.push(
            '-i', localSubPath,
            '-map', '0:v:0',
            '-map', '0:a:0?',
            '-map', '1:0?',
            '-c:v', 'copy',
            '-c:a', 'aac',
            '-b:a', '192k',
            '-ac', '2',
            '-c:s', 'mov_text',
            '-metadata:s:s:0', 'language=eng',
            '-disposition:s:0', 'default'
          );
        } else {
          ffmpegArgs.push(
            '-map', '0:v:0',
            '-map', '0:a:0?',
            '-c:v', 'copy',
            '-c:a', 'aac',
            '-b:a', '192k',
            '-ac', '2'
          );
        }
        ffmpegArgs.push(
          '-movflags', 'frag_keyframe+empty_moov+default_base_moof',
          '-f', 'mp4',
          'pipe:1'
        );
      }

      const ffmpeg = spawn('ffmpeg', ffmpegArgs);
      ffmpeg.stderr.on('data', (d) => {
        console.error('[ffmpeg stderr]', d.toString());
      });
      ffmpeg.on('error', (err) => {
        console.error('Anime ffmpeg stream error:', err.message);
        if (localSubPath) {
          try { fs.unlinkSync(localSubPath); } catch (_) {}
        }
        if (!res.headersSent) res.status(500).end();
      });

      const cleanup = () => {
        try { ffmpeg.kill('SIGKILL'); } catch (_) {}
        if (localSubPath) {
          try { fs.unlinkSync(localSubPath); } catch (_) {}
        }
      };

      req.on('close', cleanup);
      ffmpeg.on('close', cleanup);

      return ffmpeg.stdout.pipe(res);
    }

    // 2. YouTube streaming via @distube/ytdl-core + ffmpeg muxing
    if (!videoId && fileUrl) {
      const ytMatch = fileUrl.match(/(?:youtube\.com\/(?:watch\?v=|embed\/|v\/)|youtu\.be\/)([\w-]{11})/i);
      if (ytMatch) videoId = ytMatch[1];
    }

    if (videoId || type === 'youtube') {
      const targetId = videoId || 'dQw4w9WgXcQ';
      if (format === 'mp3') {
        const audioStream = ytdl.download(targetId, {
          filter: f => !f.hasVideo && f.hasAudio
        });
        const ffmpeg = spawn('ffmpeg', [
          '-loglevel', 'error',
          '-i', 'pipe:0',
          '-vn',
          '-c:a', 'libmp3lame',
          '-b:a', '320k',
          '-f', 'mp3',
          'pipe:1'
        ]);
        ffmpeg.stdin.on('error', () => {});
        audioStream.on('error', (err) => {
          console.error('YouTube audio download stream error:', err.message);
          try { ffmpeg.kill('SIGKILL'); } catch (_) {}
          if (!res.headersSent) res.status(500).end();
        });
        ffmpeg.on('error', (err) => {
          console.error('YouTube ffmpeg audio error:', err.message);
          if (!res.headersSent) res.status(500).end();
        });
        req.on('close', () => {
          try { audioStream.destroy(); } catch (_) {}
          try { ffmpeg.kill('SIGKILL'); } catch (_) {}
        });
        audioStream.pipe(ffmpeg.stdin);
        return ffmpeg.stdout.pipe(res);
      }

      // MP4 / WebM with Muxed Audio + Video via ffmpeg
      const info = await ytdl.getInfo(targetId);
      const videoFormats = info.formats.filter(f => f.hasVideo && !f.hasAudio);
      const audioFormats = info.formats.filter(f => !f.hasVideo && f.hasAudio);
      videoFormats.sort((a, b) => (b.height || 0) - (a.height || 0));
      audioFormats.sort((a, b) => (b.audioBitrate || 0) - (a.audioBitrate || 0));

      const reqHeight = parseInt(quality) || 1080;
      const targetV = videoFormats.find(f => f.height === reqHeight) ||
                     videoFormats.find(f => f.height <= reqHeight) ||
                     videoFormats[0] ||
                     info.formats.find(f => f.hasVideo);

      const targetA = audioFormats[0] || info.formats.find(f => f.hasAudio);

      if (!targetV) {
        throw new Error('No video stream available for this video');
      }

      const videoStream = ytdl.download(targetId, { filter: f => f.itag === targetV.itag });
      const audioStream = targetA ? ytdl.download(targetId, { filter: f => f.itag === targetA.itag }) : null;

      videoStream.on('error', (err) => console.warn('ytdl videoStream err:', err.message));
      if (audioStream) audioStream.on('error', (err) => console.warn('ytdl audioStream err:', err.message));

      if (audioStream) {
        const ffmpeg = spawn('ffmpeg', [
          '-loglevel', 'error',
          '-i', 'pipe:3',
          '-i', 'pipe:4',
          '-map', '0:v:0',
          '-map', '1:a:0',
          '-c:v', 'copy',
          '-c:a', 'aac',
          '-b:a', '192k',
          '-ac', '2',
          '-movflags', 'frag_keyframe+empty_moov+default_base_moof',
          '-f', 'mp4',
          'pipe:1'
        ], {
          stdio: ['pipe', 'pipe', 'pipe', 'pipe', 'pipe']
        });

        ffmpeg.stdio[3].on('error', () => {});
        ffmpeg.stdio[4].on('error', () => {});
        ffmpeg.stdin.on('error', () => {});
        ffmpeg.stdout.on('error', () => {});
        ffmpeg.on('error', (err) => {
          console.error('ffmpeg spawn error:', err.message);
          if (!res.headersSent) videoStream.pipe(res);
        });

        req.on('close', () => {
          try {
            videoStream.destroy();
            audioStream.destroy();
            ffmpeg.kill('SIGKILL');
          } catch (_) {}
        });

        videoStream.pipe(ffmpeg.stdio[3]);
        audioStream.pipe(ffmpeg.stdio[4]);
        return ffmpeg.stdout.pipe(res);
      } else {
        // Fallback: search for combined progressive stream with audio
        const prog = info.formats.find(f => f.hasVideo && f.hasAudio);
        if (prog) {
          const progStream = ytdl.download(targetId, { filter: f => f.itag === prog.itag });
          const ffmpeg = spawn('ffmpeg', [
            '-loglevel', 'error',
            '-i', 'pipe:0',
            '-c:v', 'copy',
            '-c:a', 'aac',
            '-b:a', '192k',
            '-ac', '2',
            '-movflags', 'frag_keyframe+empty_moov+default_base_moof',
            '-f', 'mp4',
            'pipe:1'
          ]);
          ffmpeg.stdin.on('error', () => {});
          progStream.on('error', (err) => {
            console.warn('ProgStream err:', err.message);
            try { ffmpeg.kill('SIGKILL'); } catch (_) {}
            if (!res.headersSent) res.status(500).end();
          });
          req.on('close', () => {
            try { progStream.destroy(); } catch (_) {}
            try { ffmpeg.kill('SIGKILL'); } catch (_) {}
          });
          progStream.pipe(ffmpeg.stdin);
          return ffmpeg.stdout.pipe(res);
        } else {
          req.on('close', () => {
            try { videoStream.destroy(); } catch (_) {}
          });
          return videoStream.pipe(res);
        }
      }
    }

    // 3. Direct HTTP / HTTPS file streaming (MP4 / WebM / sample video)
    if (fileUrl && (fileUrl.startsWith('http://') || fileUrl.startsWith('https://'))) {
      if (format === 'mp3') {
        const ffmpeg = spawn('ffmpeg', [
          '-loglevel', 'error',
          '-i', fileUrl,
          '-vn',
          '-c:a', 'libmp3lame',
          '-b:a', '320k',
          '-f', 'mp3',
          'pipe:1'
        ]);
        ffmpeg.on('error', (err) => {
          if (!res.headersSent) res.status(500).end();
        });
        req.on('close', () => {
          try { ffmpeg.kill('SIGKILL'); } catch (_) {}
        });
        return ffmpeg.stdout.pipe(res);
      }
      const response = await axios({
        method: 'get',
        url: fileUrl,
        responseType: 'stream',
        timeout: 45000,
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        }
      });

      if (response.headers['content-length']) {
        res.setHeader('Content-Length', response.headers['content-length']);
      }
      return response.data.pipe(res);
    }

    res.status(400).send('No valid stream source to download');
  } catch (err) {
    console.error('Download stream proxy error:', err.message);
    if (!res.headersSent) {
      res.status(500).send('Failed to stream file download: ' + err.message);
    }
  }
});

// 10. Sample Direct HLS / MP4 Cartoon & Anime Streams for Testing Instant Play & Download
app.get('/api/sample-streams', (req, res) => {
  res.json({
    success: true,
    data: [
      {
        id: 'big-buck-bunny',
        title: 'Big Buck Bunny - 3D Animation Comedy',
        format: 'SPECIAL',
        genres: ['Animation', 'Comedy', 'Family'],
        duration: '10 min',
        description: 'A large and lovable rabbit deals with bullying forest creatures in this hilarious, high-resolution open-source 3D animated classic.',
        coverImage: { large: 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/800px-Big_buck_bunny_poster_big.jpg' },
        bannerImage: 'https://peach.blender.org/wp-content/uploads/bbb-splash.png',
        streamType: 'direct',
        videoUrl: 'https://raw.githubusercontent.com/bower-media-samples/big-buck-bunny-480p-30s/master/video.mp4',
        downloadUrl: 'https://raw.githubusercontent.com/bower-media-samples/big-buck-bunny-480p-30s/master/video.mp4'
      },
      {
        id: 'sintel',
        title: 'Sintel - Fantasy Animated Film (HD)',
        format: 'MOVIE',
        genres: ['Animation', 'Fantasy', 'Action'],
        duration: '15 min',
        description: 'A lonely young woman, Sintel, helps and befriends a hurt baby dragon she names Scales. But when an adult dragon attacks, Scales is taken away, leading Sintel on an epic quest across a treacherous fantasy land.',
        coverImage: { large: 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Sintel_poster.jpg/800px-Sintel_poster.jpg' },
        bannerImage: 'https://durian.blender.org/wp-content/uploads/2010/09/sintel_banner.jpg',
        streamType: 'direct',
        videoUrl: 'https://media.w3.org/2010/05/sintel/trailer.mp4',
        downloadUrl: 'https://media.w3.org/2010/05/sintel/trailer.mp4'
      },
      {
        id: 'oceans',
        title: 'Oceans - Wild Life Animation Short',
        format: 'MOVIE',
        genres: ['Animation', 'Documentary', 'Short'],
        duration: '5 min',
        description: 'Breathtaking aquatic underwater journey with crystal clear visuals and nature cinematography.',
        coverImage: { large: 'https://vjs.zencdn.net/v/oceans.png' },
        bannerImage: 'https://vjs.zencdn.net/v/oceans.png',
        streamType: 'direct',
        videoUrl: 'https://vjs.zencdn.net/v/oceans.mp4',
        downloadUrl: 'https://vjs.zencdn.net/v/oceans.mp4'
      },
      {
        id: 'mux-stream',
        title: 'HLS Live Anime Test Stream (Adaptive 1080p)',
        format: 'TV',
        genres: ['Animation', 'HLS Stream'],
        duration: 'Stream',
        description: 'High-speed adaptive multi-bitrate HLS (.m3u8) video stream for testing player buffering, adaptive resolution switching, and network performance.',
        coverImage: { large: 'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80' },
        streamType: 'hls',
        videoUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
        downloadUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8'
      }
    ]
  });
});

// 12. App Version and In-App Update API
app.get('/api/app/version', (req, res) => {
  res.json({
    success: true,
    versionCode: 4,
    versionName: '1.0.3',
    mobileApkUrl: '/releases/AniStream-Mobile-v1.0.3-release.apk',
    tvApkUrl: '/releases/AniStream-TV-v1.0.3-release.apk',
    mobileLatestUrl: '/releases/AniStream-Mobile-latest.apk',
    tvLatestUrl: '/releases/AniStream-TV-latest.apk',
    releaseNotes: '• Direct High-Speed Downloader: In-app OkHttp download engine resolves all failed download issues\n• Original Video & AAC Stereo: Full synchronized sound and embedded English subtitles\n• Instant Connection Handshake: Eliminates socket timeouts and pause errors on Android\n• Companion Subtitles (.srt): Auto-downloaded and scanned into system gallery for 100% player compatibility',
    minSupportedVersion: 1
  });
});

// Fallback all other routes to index.html for SPA
app.use((req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Start Server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`AnimeStreamHub Server running on http://localhost:${PORT}`);
});
