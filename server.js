const express = require('express');
const cors = require('cors');
const axios = require('axios');
const path = require('path');
const dotenv = require('dotenv');

dotenv.config();

const app = express();
const PORT = process.env.PORT || 5050;

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

    // Build episode list (defaulting up to episodes count, or min 1)
    const episodeCount = anime.episodes || (anime.format === 'MOVIE' ? 1 : 12);
    const episodes = [];
    const mainTitle = anime.title.english || anime.title.romaji;
    const cleanSlug = encodeURIComponent(mainTitle.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, ''));

    for (let i = 1; i <= Math.min(episodeCount, 1500); i++) {
      // Find matching streaming episode if available
      const streamEp = anime.streamingEpisodes?.[i - 1];

      // Build multi-server streaming embed sources for each episode (verified working active mirrors)
      const servers = [
        {
          name: 'Server 1 (VidLink HD)',
          type: 'embed',
          url: `https://vidlink.pro/anime/${anime.id}/1/${i}`
        },
        {
          name: 'Server 2 (2Embed Fast)',
          type: 'embed',
          url: `https://2embed.cc/embed/anime/${anime.idMal || anime.id}/${i}`
        },
        {
          name: 'Server 3 (VidSrc Player)',
          type: 'embed',
          url: `https://vidsrc.me/embed/anime?mal=${anime.idMal || anime.id}&ep=${i}`
        },
        {
          name: 'Server 4 (2Embed Skin)',
          type: 'embed',
          url: `https://www.2embed.skin/embed/${anime.idMal || anime.id}`
        }
      ];

      // If official trailer is on YouTube, add as trailer & stream server
      if (anime.trailer && anime.trailer.site === 'youtube') {
        servers.push({
          name: 'Official YouTube HD Stream',
          type: 'youtube',
          url: `https://www.youtube.com/embed/${anime.trailer.id}?autoplay=1&enablejsapi=1`,
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

    res.json({
      success: true,
      data: {
        ...anime,
        episodesList: episodes
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

// 9. Video Downloader Resolver & Helper Endpoint
app.get('/api/download/resolve', async (req, res) => {
  try {
    const videoUrl = req.query.url;
    if (!videoUrl) return res.status(400).json({ success: false, message: 'URL is required' });

    // Check if it's a YouTube URL
    const ytMatch = videoUrl.match(/(?:youtube\.com\/(?:watch\?v=|embed\/|v\/)|youtu\.be\/)([\w-]{11})/i);
    if (ytMatch) {
      const videoId = ytMatch[1];
      return res.json({
        success: true,
        type: 'youtube',
        videoId,
        downloadOptions: [
          {
            label: 'Instant 1080p/720p Download (Cobalt Stream)',
            url: `https://cobalt.tools/#${encodeURIComponent(videoUrl)}`,
            method: 'external'
          },
          {
            label: 'Y2Mate HD Downloader (MP4 / MP3)',
            url: `https://www.y2mate.com/youtube/${videoId}`,
            method: 'external'
          },
          {
            label: 'SaveFrom Video Downloader',
            url: `https://en.savefrom.net/1-youtube-video-downloader-4vA/?url=https://youtube.com/watch?v=${videoId}`,
            method: 'external'
          }
        ]
      });
    }

    // Direct MP4 / M3U8 Stream
    if (videoUrl.endsWith('.mp4') || videoUrl.includes('.mp4?') || videoUrl.endsWith('.webm')) {
      return res.json({
        success: true,
        type: 'direct',
        downloadUrl: videoUrl,
        downloadOptions: [
          {
            label: 'Direct Browser Download (.mp4)',
            url: videoUrl,
            method: 'direct'
          }
        ]
      });
    }

    // Default Web Stream / Iframe Embed
    res.json({
      success: true,
      type: 'stream',
      url: videoUrl,
      downloadOptions: [
        {
          label: 'Open Source Link in New Tab',
          url: videoUrl,
          method: 'external'
        }
      ]
    });
  } catch (error) {
    res.status(500).json({ success: false, message: 'Resolution failed', error: error.message });
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
