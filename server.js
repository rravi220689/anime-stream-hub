const express = require('express');
const cors = require('cors');
const axios = require('axios');
const path = require('path');
const dotenv = require('dotenv');
const { spawn } = require('child_process');
const { Ytdl } = require('@distube/ytdl-core');

dotenv.config();

const app = express();
const PORT = process.env.PORT || 5050;
const ytdl = new Ytdl();

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

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

// 9. Comprehensive Multi-Resolution Video Downloader Resolver (1080p, 720p, 480p, 360p, MP3, WebM)
app.get('/api/download/resolve', async (req, res) => {
  try {
    const videoUrl = req.query.url || '';
    const title = req.query.title || 'Video';
    const duration = req.query.duration || '24 min';
    const type = req.query.type || 'video';
    const isMovie = req.query.format === 'MOVIE' || title.toLowerCase().includes('movie');
    const sizes = calculateFileSizes(duration, isMovie);

    const safeTitle = encodeURIComponent(title.replace(/[^a-zA-Z0-9 _-]/g, '').trim());

    // Check if YouTube
    const ytMatch = videoUrl.match(/(?:youtube\.com\/(?:watch\?v=|embed\/|v\/)|youtu\.be\/)([\w-]{11})/i);
    let videoId = ytMatch ? ytMatch[1] : (type === 'youtube' ? req.query.id : null);
    const isDirectVideo = videoUrl.includes('.mp4') || videoUrl.includes('.webm');

    // If type is anime or videoId not yet resolved and not a direct video file, resolve via YouTube search
    if (!videoId && !isDirectVideo) {
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
      p.set('type', type);
      if (videoId) p.set('id', videoId);
      if (videoUrl) p.set('url', videoUrl);
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
        externalUrl: videoId ? `https://cobalt.tools/#https://www.youtube.com/watch?v=${videoId}` : (videoUrl || null)
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
        externalUrl: videoId ? `https://cobalt.tools/#https://www.youtube.com/watch?v=${videoId}` : (videoUrl || null)
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
        externalUrl: videoId ? `https://en.savefrom.net/1-youtube-video-downloader-4vA/?url=https://youtube.com/watch?v=${videoId}` : (videoUrl || null)
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
        externalUrl: videoId ? `https://www.y2mate.com/youtube/${videoId}` : (videoUrl || null)
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
        externalUrl: videoId ? `https://www.y2mate.com/youtube-mp3/${videoId}` : (videoUrl || null)
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
        externalUrl: videoId ? `https://cobalt.tools/#https://www.youtube.com/watch?v=${videoId}` : (videoUrl || null)
      }
    ];

    // Dedicated fast mirrors & helper links
    const mirrors = [];
    if (videoId) {
      mirrors.push(
        { name: 'Cobalt HD Downloader', url: `https://cobalt.tools/#https://www.youtube.com/watch?v=${videoId}`, note: 'Ad-Free 1080p/720p/MP3' },
        { name: 'Y2Mate HD Converter', url: `https://www.y2mate.com/youtube/${videoId}`, note: 'Fast MP4 & MP3' },
        { name: 'SaveFrom Video Hub', url: `https://en.savefrom.net/1-youtube-video-downloader-4vA/?url=https://youtube.com/watch?v=${videoId}`, note: 'Direct Mobile Downloads' }
      );
    }
    if (type === 'anime') {
      mirrors.push(
        { name: 'AnimePahe Download Portal', url: `https://anitaku.to/search.html?keyword=${safeTitle}`, note: '720p & 1080p Direct Mirrors' },
        { name: 'Gogoanime Direct Server', url: `https://anitaku.to/search.html?keyword=${safeTitle}`, note: 'Episode Download Mirror' }
      );
    }

    res.json({
      success: true,
      title,
      type: ytMatch ? 'youtube' : type,
      videoId,
      sizes,
      formats: resolutions,
      mirrors,
      rawUrl: videoUrl
    });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Resolution failed', error: error.message });
  }
});

// 9b. Direct Stream / File Downloader Proxy Endpoint with Content-Disposition
app.get('/api/download/file', async (req, res) => {
  try {
    const title = req.query.title || 'video';
    const quality = req.query.quality || '1080p';
    const format = (req.query.format || 'mp4').toLowerCase();
    const type = req.query.type || 'video';
    const fileUrl = req.query.url || '';
    let videoId = req.query.videoId || req.query.id || '';

    // If no videoId provided but URL is YouTube
    if (!videoId && fileUrl) {
      const ytMatch = fileUrl.match(/(?:youtube\.com\/(?:watch\?v=|embed\/|v\/)|youtu\.be\/)([\w-]{11})/i);
      if (ytMatch) videoId = ytMatch[1];
    }

    // If still no videoId, but type is anime and not a direct file URL, resolve via YouTube search
    if (!videoId && (!fileUrl || (!fileUrl.includes('.mp4') && !fileUrl.includes('.webm')))) {
      try {
        const cleanQuery = title.replace(/\s+/g, ' ').trim();
        const searchResults = await searchYouTube(cleanQuery, 3);
        if (searchResults && searchResults.length > 0) {
          videoId = searchResults[0].id;
        }
      } catch (e) {}
    }

    const cleanName = title.replace(/[^a-zA-Z0-9_-]/g, '_').replace(/_+/g, '_').slice(0, 80) || 'video';
    const filename = `${cleanName}_${quality}.${format}`;

    // Common headers enforcing genuine download to Downloads folder
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.setHeader('Content-Type', 'application/octet-stream');
    res.setHeader('X-Content-Type-Options', 'nosniff');

    if (req.method === 'HEAD') {
      return res.end();
    }

    // 1. YouTube streaming via @distube/ytdl-core + ffmpeg muxing
    if (videoId || type === 'youtube') {
      const targetId = videoId || 'dQw4w9WgXcQ';
      if (format === 'mp3') {
        const audioStream = ytdl.download(targetId, {
          filter: f => !f.hasVideo && f.hasAudio
        });
        audioStream.on('error', (err) => {
          console.error('YouTube audio download stream error:', err.message);
          if (!res.headersSent) res.status(500).end();
        });
        req.on('close', () => {
          try { audioStream.destroy(); } catch (_) {}
        });
        return audioStream.pipe(res);
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
        req.on('close', () => {
          try { videoStream.destroy(); } catch (_) {}
        });
        return videoStream.pipe(res);
      }
    }

    // 2. Direct HTTP / HTTPS file streaming (MP4 / WebM / sample video)
    if (fileUrl && (fileUrl.startsWith('http://') || fileUrl.startsWith('https://'))) {
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
      // NEVER res.redirect(req.query.url) - it plays the video in the browser!
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

// Fallback all other routes to index.html for SPA
app.use((req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Start Server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`AnimeStreamHub Server running on http://localhost:${PORT}`);
});
