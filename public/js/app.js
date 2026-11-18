/**
 * AnimeStreamHub - Interactive Single Page Application Controller
 * Handles mobile & desktop responsive views, multi-server anime/cartoon streaming,
 * YouTube video integration, custom URL playing, and download management.
 */

class AnimeStreamApp {
  constructor() {
    this.currentTab = 'home';
    this.currentCategory = 'latest';
    this.searchScope = 'all';
    this.currentItem = null;
    this.currentEpisode = 1;
    this.currentServerIndex = 0;
    this.activeStreamType = 'embed'; // 'embed', 'native', 'youtube'
    this.searchTimeout = null;
    this.loadTimeout = null;
    this.hls = null;
    this.isCinemaMode = false;

    // Pagination State
    this.currentPage = 1;
    this.totalPages = 1;
    this.hasNextPage = false;
    this.totalItems = 0;
    this.currentSearchQuery = '';

    // Directory State
    this.directoryView = 'grid'; // 'grid' or 'list'
    this.dirFilter = {
      sort: 'popularity',
      status: '',
      format: '',
      genre: '',
      search: ''
    };
    this.dirSearchTimeout = null;
    this.dirItems = [];

    // Load persistent state from localStorage
    this.watchlist = this.loadStorage('animestream_watchlist', []);
    this.history = this.loadStorage('animestream_history', {});

    // Elements cache
    this.initElements();
    this.bindEvents();
    this.updateWatchlistCounter();

    // Initial Load - Show Latest Releases on First Page!
    this.loadFeaturedHero();
    this.switchCategory('latest');
  }

  // Safe localStorage helper
  loadStorage(key, fallback) {
    try {
      const data = localStorage.getItem(key);
      return data ? JSON.parse(data) : fallback;
    } catch (e) {
      console.warn('Storage read error:', e);
      return fallback;
    }
  }

  saveStorage(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
      console.warn('Storage write error:', e);
    }
  }

  initElements() {
    this.mediaGrid = document.getElementById('mediaGrid');
    this.sectionTitle = document.getElementById('sectionTitle');
    this.sectionCount = document.getElementById('sectionCount');
    this.heroSlider = document.getElementById('heroSlider');
    this.playerSection = document.getElementById('playerSection');
    this.videoContainer = document.getElementById('videoContainer');
    this.embedIframe = document.getElementById('embedIframe');
    this.nativeVideo = document.getElementById('nativeVideo');
    this.playerLoading = document.getElementById('playerLoading');
    this.playerTitle = document.getElementById('playerTitle');
    this.playerSubtitle = document.getElementById('playerSubtitle');
    this.serverList = document.getElementById('serverList');
    this.externalMirrorsRow = document.getElementById('externalMirrorsRow');
    this.externalMirrorsList = document.getElementById('externalMirrorsList');
    this.episodeSection = document.getElementById('episodeSection');
    this.episodeGrid = document.getElementById('episodeGrid');
    this.totalEpCount = document.getElementById('totalEpCount');
    this.prevEpBtn = document.getElementById('prevEpBtn');
    this.nextEpBtn = document.getElementById('nextEpBtn');
    this.playerBookmarkBtn = document.getElementById('playerBookmarkBtn');
    this.searchDrawer = document.getElementById('searchDrawer');
    this.globalSearchInput = document.getElementById('globalSearchInput');
    this.clearSearchBtn = document.getElementById('clearSearchBtn');
    this.emptyState = document.getElementById('emptyState');
    this.youtubeHubSection = document.getElementById('youtubeHubSection');
    this.youtubeGrid = document.getElementById('youtubeGrid');
    this.miniPlayer = document.getElementById('miniPlayer');
    this.miniTitle = document.getElementById('miniTitle');
    this.miniSub = document.getElementById('miniSub');
    this.cinemaOverlay = document.getElementById('cinemaOverlay');
    this.pasteModal = document.getElementById('pasteModal');
    this.downloadModal = document.getElementById('downloadModal');
    this.toast = document.getElementById('toast');
    this.toastMsg = document.getElementById('toastMsg');

    // Pagination Elements
    this.paginationBar = document.getElementById('paginationBar');
    this.currentPageNum = document.getElementById('currentPageNum');
    this.totalPagesNum = document.getElementById('totalPagesNum');
    this.prevPageBtn = document.getElementById('prevPageBtn');
    this.nextPageBtn = document.getElementById('nextPageBtn');
    this.pageNumberButtons = document.getElementById('pageNumberButtons');
    this.jumpPageInput = document.getElementById('jumpPageInput');

    // Directory Elements
    this.directorySection = document.getElementById('directorySection');
    this.directoryContent = document.getElementById('directoryContent');
    this.dirSearchInput = document.getElementById('dirSearchInput');
    this.dirSort = document.getElementById('dirSort');
    this.dirStatus = document.getElementById('dirStatus');
    this.dirFormat = document.getElementById('dirFormat');
    this.dirGenre = document.getElementById('dirGenre');
    this.viewModeGrid = document.getElementById('viewModeGrid');
    this.viewModeList = document.getElementById('viewModeList');
  }

  bindEvents() {
    // Window scroll to handle sticky mini-player on mobile
    window.addEventListener('scroll', () => {
      if (!this.currentItem || this.playerSection.classList.contains('hidden')) {
        this.miniPlayer.classList.add('hidden');
        return;
      }
      const rect = this.videoContainer.getBoundingClientRect();
      // If player scrolled off top of screen, show mini-player
      if (rect.bottom < 60) {
        this.miniPlayer.classList.remove('hidden');
      } else {
        this.miniPlayer.classList.add('hidden');
      }
    }, { passive: true });

    // Handle ESC key to exit modals or cinema mode
    window.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        if (this.isCinemaMode) this.toggleCinemaMode();
        this.closePasteModal();
        this.closeDownloadModal();
      }
    });
  }

  // Re-run Lucide icons
  renderIcons() {
    if (window.lucide) {
      window.lucide.createIcons();
    }
  }

  // Toast Notification
  showToast(message, icon = 'check-circle') {
    this.toastMsg.textContent = message;
    this.toast.classList.remove('hidden');
    const toastIconEl = document.getElementById('toastIcon');
    if (toastIconEl) toastIconEl.setAttribute('data-lucide', icon);
    this.renderIcons();

    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => {
      this.toast.classList.add('hidden');
    }, 3200);
  }

  // -------------------------------------------------------------
  // Navigation & Category Handling
  // -------------------------------------------------------------
  navigate(tab) {
    this.currentTab = tab;

    // Update active nav buttons on desktop & mobile
    document.querySelectorAll('.nav-btn, .mobile-nav-item').forEach(el => {
      el.classList.toggle('active', el.getAttribute('data-tab') === tab);
    });

    if (tab === 'youtube') {
      this.directorySection.classList.add('hidden');
      this.paginationBar.classList.add('hidden');
      this.showYouTubeHub();
      return;
    }

    if (tab === 'directory') {
      this.youtubeHubSection.classList.add('hidden');
      this.mediaGrid.classList.add('hidden');
      document.querySelector('.category-bar').classList.add('hidden');
      document.querySelector('.section-heading-row').classList.add('hidden');
      this.showDirectory(1);
      window.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }

    // Default media views
    this.youtubeHubSection.classList.add('hidden');
    this.directorySection.classList.add('hidden');
    document.querySelector('.category-bar').classList.remove('hidden');
    document.querySelector('.section-heading-row').classList.remove('hidden');
    this.mediaGrid.classList.remove('hidden');

    if (tab === 'watchlist') {
      this.showWatchlist();
      return;
    }

    if (tab === 'movies') {
      this.switchCategory('movies', null, 1);
    } else if (tab === 'cartoons') {
      this.switchCategory('cartoons', null, 1);
    } else {
      this.switchCategory('latest', null, 1);
    }

    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async switchCategory(category, targetButton = null, page = 1) {
    this.currentCategory = category;
    this.currentPage = page;

    // Update category pill styles
    document.querySelectorAll('.cat-pill').forEach(btn => {
      btn.classList.toggle('active', btn.getAttribute('data-category') === category);
    });

    this.emptyState.classList.add('hidden');
    this.mediaGrid.classList.remove('hidden');
    this.youtubeHubSection.classList.add('hidden');
    this.directorySection.classList.add('hidden');

    // Show skeletons
    this.renderSkeletons();

    let endpoint = `/api/anime/latest?page=${page}&limit=24`;
    let title = '⚡ Latest Episode Releases (Aired Today & This Week)';

    if (category === 'latest' || category === 'all') {
      endpoint = `/api/anime/latest?page=${page}&limit=24`;
      title = '⚡ Latest Episode Releases (Aired Today & This Week)';
    } else if (category === 'trending') {
      endpoint = `/api/anime/trending?page=${page}&limit=24`;
      title = 'Trending Anime Series (Japan & Global)';
    } else if (category === 'popular') {
      endpoint = `/api/anime/popular?page=${page}&limit=24`;
      title = 'All-Time Most Popular Series';
    } else if (category === 'movies') {
      endpoint = `/api/anime/movies?page=${page}&limit=24`;
      title = 'Animated Feature Films & Movies';
    } else if (category === 'cartoons') {
      endpoint = `/api/anime/cartoons?page=${page}&limit=24`;
      title = 'Cartoons & Family Animations';
    } else if (category === 'youtube-tom') {
      endpoint = '/api/youtube/cartoons?category=tom-jerry';
      title = 'Tom and Jerry - Full Cartoon Episodes (YouTube)';
    } else if (category === 'youtube-pokemon') {
      endpoint = '/api/youtube/cartoons?category=pokemon';
      title = 'Pokémon Animated Episodes & Specials';
    } else if (category === 'sample-streams') {
      endpoint = '/api/sample-streams';
      title = 'Open Source 4K Animations & HLS Streams';
    }

    this.sectionTitle.textContent = title;
    this.sectionCount.textContent = 'Loading...';

    try {
      const res = await fetch(endpoint);
      const json = await res.json();
      if (json.success && json.data && json.data.length > 0) {
        this.renderMediaGrid(json.data);
        this.sectionCount.textContent = `${json.data.length} Available`;

        // Update pagination if available
        if (json.pageInfo) {
          this.updatePagination(json.pageInfo);
        } else {
          this.paginationBar.classList.add('hidden');
        }
      } else {
        this.mediaGrid.innerHTML = '';
        this.emptyState.classList.remove('hidden');
        this.sectionCount.textContent = '0 Available';
        this.paginationBar.classList.add('hidden');
      }
    } catch (err) {
      console.error('Fetch error:', err);
      this.mediaGrid.innerHTML = '';
      this.emptyState.classList.remove('hidden');
      this.sectionCount.textContent = 'Error loading';
      this.paginationBar.classList.add('hidden');
    }
  }

  // Render Skeletons during loading
  renderSkeletons() {
    let skeletons = '';
    for (let i = 0; i < 12; i++) {
      skeletons += '<div class="card-skeleton skeleton"></div>';
    }
    this.mediaGrid.innerHTML = skeletons;
  }

  // -------------------------------------------------------------
  // Featured Hero Banner
  // -------------------------------------------------------------
  async loadFeaturedHero() {
    try {
      // First try fetching latest release for hero
      let res = await fetch('/api/anime/latest?limit=4');
      let json = await res.json();
      if (!json.success || !json.data || json.data.length === 0) {
        res = await fetch('/api/anime/trending?limit=4');
        json = await res.json();
      }
      if (!json.success || !json.data || json.data.length === 0) return;

      const items = json.data;
      const featured = items[0];
      const title = featured.title.english || featured.title.romaji;
      const desc = featured.description ? featured.description.replace(/<[^>]*>?/gm, '') : 'Watch the latest trending animated series and movies online.';
      const banner = featured.bannerImage || featured.coverImage.extraLarge || featured.coverImage.large;
      const targetEp = featured.latestEpisode || 1;

      this.heroSlider.innerHTML = `
        <div class="hero-slide" style="background-image: url('${banner}')">
          <div class="hero-overlay"></div>
          <div class="hero-content">
            <div class="hero-badge-group">
              <span class="hero-tag" style="background: linear-gradient(135deg, #10b981, #06b6d4);">⚡ LATEST RELEASE</span>
              ${featured.latestEpisode ? `<span class="hero-tag" style="background: rgba(99, 102, 241, 0.9);">EPISODE ${featured.latestEpisode}</span>` : ''}
              <span class="hero-tag" style="background: rgba(168, 85, 247, 0.85);">${featured.genres?.[0] || 'Animation'}</span>
              ${featured.averageScore ? `<span class="hero-score"><i data-lucide="star"></i> ${featured.averageScore}%</span>` : ''}
            </div>
            <h1 class="hero-title">${title}</h1>
            <p class="hero-desc">${desc}</p>
            <div class="hero-actions">
              <button class="btn-primary" onclick="app.playAnime(${featured.id}, ${targetEp})">
                <i data-lucide="play"></i> Watch Episode ${targetEp} Now
              </button>
              <button class="btn-secondary" onclick="app.toggleWatchlistFromHero(${featured.id}, '${encodeURIComponent(JSON.stringify(featured))}')">
                <i data-lucide="bookmark-plus"></i> Save to Watchlist
              </button>
            </div>
          </div>
        </div>
      `;
      this.renderIcons();
    } catch (e) {
      console.warn('Hero load failed:', e);
    }
  }

  // -------------------------------------------------------------
  // Media Grid Renderer (Responsive Cards)
  // -------------------------------------------------------------
  renderMediaGrid(items) {
    this.mediaGrid.innerHTML = '';
    if (!items || items.length === 0) {
      this.emptyState.classList.remove('hidden');
      return;
    }
    this.emptyState.classList.add('hidden');

    items.forEach(item => {
      const card = document.createElement('div');
      const isVideo = item.type === 'youtube' || item.streamType === 'direct';
      card.className = `media-card ${isVideo ? 'is-video' : ''}`;

      const title = item.title?.english || item.title?.romaji || item.title || 'Untitled';
      const poster = item.coverImage?.large || item.coverImage?.extraLarge || item.thumbnail || 'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600';
      const format = item.format || (item.type === 'youtube' ? 'YOUTUBE' : 'HD');
      const score = item.averageScore ? `${item.averageScore}%` : null;
      const subInfo = item.channel || item.genres?.slice(0, 2).join(' • ') || (item.seasonYear ? `${item.seasonYear}` : '');
      const targetEp = item.latestEpisode || 1;

      // Special latest release badge
      const epBadge = item.latestEpisode
        ? `<span class="badge-ep" style="background: linear-gradient(135deg, #10b981, #06b6d4); font-weight: 800;">Ep ${item.latestEpisode} • NEW</span>`
        : (item.episodes ? `<span class="badge-ep">Ep ${item.episodes}</span>` : '');

      card.innerHTML = `
        <div class="card-poster-wrapper">
          <img class="card-poster" src="${poster}" alt="${title}" loading="lazy" onerror="this.src='https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600'">
          
          <div class="card-badges">
            <span class="badge-format">${format}</span>
            ${score ? `<span class="badge-score"><i data-lucide="star"></i> ${score}</span>` : ''}
          </div>

          ${epBadge}
          ${item.duration ? `<span class="badge-duration">${item.duration}</span>` : ''}

          <div class="play-hover-btn">
            <div class="play-circle">
              <i data-lucide="play"></i>
            </div>
          </div>
        </div>

        <div class="card-info">
          <h3 class="card-title" title="${title}">${title}</h3>
          <p class="card-sub">${subInfo}</p>
        </div>
      `;

      card.addEventListener('click', () => {
        if (item.type === 'youtube') {
          this.playYouTubeVideo(item);
        } else if (item.streamType === 'direct' || item.streamType === 'hls') {
          this.playDirectVideo(item);
        } else {
          this.playAnime(item.id, targetEp);
        }
      });

      this.mediaGrid.appendChild(card);
    });

    this.renderIcons();
  }

  // -------------------------------------------------------------
  // Video Player System
  // -------------------------------------------------------------

  // 1. Play Anime Series or Movie
  async playAnime(id, episodeNumber = 1) {
    this.showPlayerLoading(true);
    this.playerSection.classList.remove('hidden');
    this.scrollToPlayer();

    try {
      const res = await fetch(`/api/anime/details/${id}`);
      const json = await res.json();
      if (!json.success || !json.data) throw new Error('Details not available');

      const anime = json.data;
      const totalEpisodes = anime.episodes || anime.episodesList?.length || 1;
      this.currentItem = {
        type: 'anime',
        id: anime.id,
        idMal: anime.idMal,
        title: anime.title.english || anime.title.romaji,
        poster: anime.coverImage.large,
        episodesList: anime.episodesList || [],
        totalEpisodes: totalEpisodes,
        genres: anime.genres || [],
        externalMirrors: anime.externalMirrors || null
      };

      this.currentEpisode = Math.max(1, Math.min(episodeNumber, totalEpisodes));
      this.currentServerIndex = 0;
      this.renderPlayerMeta();
      this.renderEpisodeGrid();
      this.renderExternalMirrors();
      this.loadStreamForCurrentEpisode();
      this.saveHistory();
    } catch (err) {
      console.error('Play anime failed:', err);
      this.showToast('Could not load anime stream. Try another server or link.', 'alert-triangle');
      this.showPlayerLoading(false);
    }
  }

  // 2. Play Direct MP4 / M3U8 Stream
  playDirectVideo(item) {
    this.currentItem = {
      type: 'direct',
      id: item.id || 'custom-stream',
      title: item.title,
      poster: item.coverImage?.large || item.thumbnail || '',
      videoUrl: item.videoUrl,
      downloadUrl: item.downloadUrl || item.videoUrl,
      streamType: item.streamType || (item.videoUrl.endsWith('.m3u8') ? 'hls' : 'direct')
    };

    this.currentEpisode = 1;
    this.episodeSection.classList.add('hidden');
    if (this.externalMirrorsRow) this.externalMirrorsRow.classList.add('hidden');
    this.playerSection.classList.remove('hidden');
    this.scrollToPlayer();

    this.renderPlayerMeta();
    this.serverList.innerHTML = `
      <button class="server-btn active">Direct High-Speed Stream</button>
    `;

    this.embedIframe.classList.add('hidden');
    this.embedIframe.src = '';
    this.nativeVideo.classList.remove('hidden');

    this.showPlayerLoading(true);

    if (this.currentItem.streamType === 'hls' && Hls.isSupported()) {
      if (this.hls) this.hls.destroy();
      this.hls = new Hls({ enableWorker: true });
      this.hls.loadSource(this.currentItem.videoUrl);
      this.hls.attachMedia(this.nativeVideo);
      this.hls.on(Hls.Events.MANIFEST_PARSED, () => {
        this.showPlayerLoading(false);
        this.nativeVideo.play().catch(() => {});
      });
      this.hls.on(Hls.Events.ERROR, (event, data) => {
        if (data.fatal) {
          console.warn('HLS fatal error:', data);
          this.showPlayerLoading(false);
        }
      });
    } else {
      this.nativeVideo.src = this.currentItem.videoUrl;
      this.nativeVideo.onloadeddata = () => {
        this.showPlayerLoading(false);
        this.nativeVideo.play().catch(() => {});
      };
      this.nativeVideo.onerror = () => {
        this.showPlayerLoading(false);
      };
    }
  }

  // 3. Play YouTube Video directly on site
  playYouTubeVideo(item) {
    const videoId = item.id;
    this.currentItem = {
      type: 'youtube',
      id: videoId,
      title: item.title,
      poster: item.thumbnail,
      embedUrl: `https://www.youtube.com/embed/${videoId}?autoplay=1&enablejsapi=1`,
      downloadUrl: `https://cobalt.tools/#https://www.youtube.com/watch?v=${videoId}`,
      channel: item.channel
    };

    this.episodeSection.classList.add('hidden');
    if (this.externalMirrorsRow) this.externalMirrorsRow.classList.add('hidden');
    this.playerSection.classList.remove('hidden');
    this.scrollToPlayer();

    this.renderPlayerMeta();

    // YouTube server options
    this.serverList.innerHTML = `
      <button class="server-btn active" onclick="app.switchYouTubeServer('embed')">YouTube Embedded HD</button>
      <button class="server-btn" onclick="app.switchYouTubeServer('nocookie')">Privacy No-Cookie Player</button>
    `;

    if (this.hls) {
      this.hls.destroy();
      this.hls = null;
    }
    this.nativeVideo.classList.add('hidden');
    this.nativeVideo.pause();
    this.nativeVideo.src = '';

    if (this.loadTimeout) clearTimeout(this.loadTimeout);
    this.embedIframe.classList.remove('hidden');
    this.showPlayerLoading(true);
    this.embedIframe.src = this.currentItem.embedUrl;
    this.embedIframe.onload = () => this.showPlayerLoading(false);
    this.loadTimeout = setTimeout(() => this.showPlayerLoading(false), 1500);
  }

  renderExternalMirrors() {
    if (!this.externalMirrorsRow || !this.externalMirrorsList) return;
    const mirrors = this.currentItem?.externalMirrors;
    if (!mirrors || (!mirrors.gogoanime && !mirrors.animepahe && !mirrors.hianime)) {
      this.externalMirrorsRow.classList.add('hidden');
      return;
    }

    this.externalMirrorsRow.classList.remove('hidden');
    this.externalMirrorsList.innerHTML = `
      ${mirrors.gogoanime ? `<a href="${mirrors.gogoanime}" target="_blank" rel="noopener" class="mirror-link-btn"><i data-lucide="play-circle"></i> Watch on Gogoanime</a>` : ''}
      ${mirrors.animepahe ? `<a href="${mirrors.animepahe}" target="_blank" rel="noopener" class="mirror-link-btn"><i data-lucide="external-link"></i> Watch on AnimePahe</a>` : ''}
      ${mirrors.hianime ? `<a href="${mirrors.hianime}" target="_blank" rel="noopener" class="mirror-link-btn"><i data-lucide="tv"></i> Watch on HiAnime</a>` : ''}
    `;
    this.renderIcons();
  }

  switchYouTubeServer(mode) {
    if (!this.currentItem || this.currentItem.type !== 'youtube') return;
    this.showPlayerLoading(true);
    const videoId = this.currentItem.id;
    if (mode === 'nocookie') {
      this.embedIframe.src = `https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1`;
    } else {
      this.embedIframe.src = `https://www.youtube.com/embed/${videoId}?autoplay=1`;
    }
    if (this.loadTimeout) clearTimeout(this.loadTimeout);
    this.embedIframe.onload = () => this.showPlayerLoading(false);
    this.loadTimeout = setTimeout(() => this.showPlayerLoading(false), 1500);
  }

  openExternalStream() {
    if (!this.currentItem) {
      this.showToast('Select an item to stream first', 'alert-circle');
      return;
    }
    const currentServer = this.currentItem.episodesList?.[this.currentEpisode - 1]?.servers?.[this.currentServerIndex];
    const streamUrl = currentServer?.url || this.embedIframe.src || this.currentItem.videoUrl;
    if (streamUrl) {
      window.open(streamUrl, '_blank', 'noopener,noreferrer');
      this.showToast('Opened stream in external player', 'external-link');
    }
  }

  // Load stream for current episode of anime
  loadStreamForCurrentEpisode() {
    if (!this.currentItem || !this.currentItem.episodesList) return;

    const epData = this.currentItem.episodesList[this.currentEpisode - 1];
    if (!epData || !epData.servers || epData.servers.length === 0) {
      this.showToast('Episode streaming servers not found', 'alert-circle');
      return;
    }

    const servers = epData.servers;
    if (this.currentServerIndex >= servers.length) {
      this.currentServerIndex = 0;
    }
    const selectedServer = servers[this.currentServerIndex] || servers[0];

    // Render Server Selector Buttons
    this.serverList.innerHTML = '';
    servers.forEach((srv, idx) => {
      const btn = document.createElement('button');
      btn.className = `server-btn ${idx === this.currentServerIndex ? 'active' : ''}`;
      btn.textContent = srv.name;
      btn.onclick = () => {
        this.currentServerIndex = idx;
        this.loadStreamForCurrentEpisode();
      };
      this.serverList.appendChild(btn);
    });

    // Native vs Iframe switch
    if (selectedServer.type === 'hls' || selectedServer.type === 'direct') {
      this.embedIframe.classList.add('hidden');
      this.embedIframe.src = '';
      this.nativeVideo.classList.remove('hidden');
      this.showPlayerLoading(true);

      if (selectedServer.type === 'hls' && Hls.isSupported()) {
        if (this.hls) this.hls.destroy();
        this.hls = new Hls({ enableWorker: true });
        this.hls.loadSource(selectedServer.url);
        this.hls.attachMedia(this.nativeVideo);
        this.hls.on(Hls.Events.MANIFEST_PARSED, () => {
          this.showPlayerLoading(false);
          this.nativeVideo.play().catch(() => {});
        });
      } else {
        this.nativeVideo.src = selectedServer.url;
        this.nativeVideo.onloadeddata = () => {
          this.showPlayerLoading(false);
          this.nativeVideo.play().catch(() => {});
        };
      }
    } else {
      if (this.hls) {
        this.hls.destroy();
        this.hls = null;
      }
      this.nativeVideo.classList.add('hidden');
      this.nativeVideo.pause();
      this.nativeVideo.src = '';

      if (this.loadTimeout) clearTimeout(this.loadTimeout);
      this.embedIframe.classList.remove('hidden');
      this.showPlayerLoading(true);
      this.embedIframe.src = selectedServer.url;
      this.embedIframe.onload = () => {
        this.showPlayerLoading(false);
      };
      this.loadTimeout = setTimeout(() => {
        this.showPlayerLoading(false);
      }, 1500);
    }

    // Update episode navigation button states
    this.prevEpBtn.disabled = this.currentEpisode <= 1;
    this.nextEpBtn.disabled = this.currentEpisode >= this.currentItem.totalEpisodes;

    // Update active episode in grid
    document.querySelectorAll('.ep-pill').forEach(pill => {
      const epNum = parseInt(pill.getAttribute('data-ep'));
      pill.classList.toggle('active', epNum === this.currentEpisode);
      if (epNum === this.currentEpisode) pill.classList.add('watched');
    });

    // Update subtitle
    this.playerSubtitle.textContent = `Streaming Episode ${this.currentEpisode} of ${this.currentItem.totalEpisodes}`;
    this.miniSub.textContent = `Episode ${this.currentEpisode}`;
  }

  // Render Episode Grid for Anime
  renderEpisodeGrid() {
    if (!this.currentItem || this.currentItem.type !== 'anime') {
      this.episodeSection.classList.add('hidden');
      return;
    }

    const episodes = this.currentItem.episodesList;
    if (!episodes || episodes.length <= 1) {
      this.episodeSection.classList.add('hidden');
      return;
    }

    this.episodeSection.classList.remove('hidden');
    this.totalEpCount.textContent = episodes.length;
    this.episodeGrid.innerHTML = '';

    // Generate episode pills
    episodes.forEach(ep => {
      const pill = document.createElement('button');
      pill.className = `ep-pill ${ep.number === this.currentEpisode ? 'active' : ''}`;
      pill.setAttribute('data-ep', ep.number);
      pill.textContent = `Ep ${ep.number}`;
      pill.onclick = () => {
        this.currentEpisode = ep.number;
        this.loadStreamForCurrentEpisode();
      };
      this.episodeGrid.appendChild(pill);
    });
  }

  playNextEpisode() {
    if (this.currentItem && this.currentEpisode < this.currentItem.totalEpisodes) {
      this.currentEpisode++;
      this.loadStreamForCurrentEpisode();
      this.saveHistory();
    }
  }

  playPreviousEpisode() {
    if (this.currentItem && this.currentEpisode > 1) {
      this.currentEpisode--;
      this.loadStreamForCurrentEpisode();
      this.saveHistory();
    }
  }

  // Render Metadata in Player bar
  renderPlayerMeta() {
    const title = this.currentItem.title;
    this.playerTitle.textContent = title;
    this.miniTitle.textContent = title;
    this.playerSubtitle.textContent = this.currentItem.type === 'anime' ? `Episode ${this.currentEpisode}` : 'Now Streaming';
    this.miniSub.textContent = this.playerSubtitle.textContent;

    // Check if in watchlist
    const isBookmarked = this.watchlist.some(w => w.id === this.currentItem.id);
    this.updateBookmarkButtonState(isBookmarked);
  }

  showPlayerLoading(show) {
    if (show) {
      this.playerLoading.classList.remove('hidden');
    } else {
      this.playerLoading.classList.add('hidden');
    }
  }

  scrollToPlayer() {
    this.playerSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  closePlayer() {
    if (this.hls) {
      this.hls.destroy();
      this.hls = null;
    }
    this.nativeVideo.pause();
    this.nativeVideo.src = '';
    this.embedIframe.src = '';
    this.playerSection.classList.add('hidden');
    this.miniPlayer.classList.add('hidden');
    if (this.externalMirrorsRow) this.externalMirrorsRow.classList.add('hidden');
    if (this.isCinemaMode) this.toggleCinemaMode();
  }

  toggleCinemaMode() {
    this.isCinemaMode = !this.isCinemaMode;
    this.cinemaOverlay.classList.toggle('hidden', !this.isCinemaMode);
    this.playerSection.style.zIndex = this.isCinemaMode ? '95' : '90';
  }

  // -------------------------------------------------------------
  // Custom URL / Paste Link Streamer Engine
  // -------------------------------------------------------------
  openPasteModal() {
    this.pasteModal.classList.remove('hidden');
    document.getElementById('customUrlInput').focus();
    this.renderIcons();
  }

  closePasteModal() {
    this.pasteModal.classList.add('hidden');
  }

  fillUrl(url, title = '') {
    document.getElementById('customUrlInput').value = url;
  }

  playCustomUrl() {
    const input = document.getElementById('customUrlInput').value.trim();
    if (!input) {
      this.showToast('Please enter a valid video or YouTube URL', 'alert-circle');
      return;
    }

    this.closePasteModal();

    // Check if YouTube
    const ytMatch = input.match(/(?:youtube\.com\/(?:watch\?v=|embed\/|v\/)|youtu\.be\/)([\w-]{11})/i);
    if (ytMatch) {
      const videoId = ytMatch[1];
      this.playYouTubeVideo({
        id: videoId,
        title: 'Custom YouTube Stream',
        thumbnail: `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
        channel: 'YouTube Video'
      });
      this.showToast('Playing YouTube Stream', 'play');
      return;
    }

    // Direct MP4 or M3U8
    if (input.endsWith('.mp4') || input.includes('.mp4?') || input.endsWith('.webm') || input.endsWith('.m3u8')) {
      const isHls = input.includes('.m3u8');
      this.playDirectVideo({
        id: 'direct-' + Date.now(),
        title: isHls ? 'Direct HLS Anime Stream' : 'Direct MP4 Video Stream',
        videoUrl: input,
        downloadUrl: input,
        streamType: isHls ? 'hls' : 'direct',
        thumbnail: 'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600'
      });
      this.showToast('Playing Direct Stream', 'play');
      return;
    }

    // Generic Web Embed (e.g. Gogoanime, Animepahe, VidSrc embed link)
    this.currentItem = {
      type: 'embed',
      id: 'custom-embed-' + Date.now(),
      title: 'Custom Stream Embed',
      embedUrl: input,
      downloadUrl: input
    };

    this.episodeSection.classList.add('hidden');
    this.playerSection.classList.remove('hidden');
    this.scrollToPlayer();
    this.renderPlayerMeta();

    this.serverList.innerHTML = `<button class="server-btn active">Custom Embed</button>`;
    this.nativeVideo.classList.add('hidden');
    this.nativeVideo.pause();
    this.embedIframe.classList.remove('hidden');
    this.showPlayerLoading(true);
    this.embedIframe.src = input;
    this.embedIframe.onload = () => this.showPlayerLoading(false);

    this.showToast('Playing Custom Stream Link', 'play');
  }

  // -------------------------------------------------------------
  // Video Downloader Engine
  // -------------------------------------------------------------
  async openDownloadModal() {
    if (!this.currentItem) {
      this.showToast('Please play a video first to download', 'alert-circle');
      return;
    }

    const modal = this.downloadModal;
    const dlThumb = document.getElementById('dlThumb');
    const dlTitle = document.getElementById('dlTitle');
    const dlSub = document.getElementById('dlSub');
    const optionsList = document.getElementById('downloadOptionsList');

    dlTitle.textContent = this.currentItem.title;
    dlSub.textContent = this.currentItem.type === 'anime' ? `Episode ${this.currentEpisode} • Full HD` : 'High Definition';
    dlThumb.style.backgroundImage = `url('${this.currentItem.poster || "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600"}')`;

    optionsList.innerHTML = '<div class="spinner" style="margin: 20px auto;"></div>';
    modal.classList.remove('hidden');

    // If direct MP4 / HLS video
    if (this.currentItem.type === 'direct' && this.currentItem.videoUrl) {
      const url = this.currentItem.videoUrl;
      optionsList.innerHTML = `
        <a href="${url}" download="${this.cleanFilename(this.currentItem.title)}.mp4" class="dl-option-btn" target="_blank">
          <div>
            <i data-lucide="download"></i>
            <strong>Direct Browser Download (.MP4)</strong>
          </div>
          <span>Instant Save</span>
        </a>
        <button class="dl-option-btn" style="background: rgba(99, 102, 241, 0.1); border-color: rgba(99, 102, 241, 0.3); color: #818cf8;" onclick="app.copyToClipboard('${url}')">
          <div>
            <i data-lucide="copy"></i>
            <span>Copy Stream Direct URL</span>
          </div>
          <span>Copy</span>
        </button>
      `;
      this.renderIcons();
      return;
    }

    // If YouTube video
    if (this.currentItem.type === 'youtube') {
      const videoId = this.currentItem.id;
      const ytUrl = `https://www.youtube.com/watch?v=${videoId}`;
      optionsList.innerHTML = `
        <a href="https://cobalt.tools/#${encodeURIComponent(ytUrl)}" class="dl-option-btn" target="_blank" rel="noopener">
          <div>
            <i data-lucide="sparkles"></i>
            <strong>Cobalt HD Downloader (Ad-Free)</strong>
          </div>
          <span>1080p / 720p</span>
        </a>
        <a href="https://www.y2mate.com/youtube/${videoId}" class="dl-option-btn" target="_blank" rel="noopener">
          <div>
            <i data-lucide="film"></i>
            <strong>Y2Mate Video & MP3 Downloader</strong>
          </div>
          <span>MP4 / MP3</span>
        </a>
        <a href="https://en.savefrom.net/1-youtube-video-downloader-4vA/?url=${encodeURIComponent(ytUrl)}" class="dl-option-btn" target="_blank" rel="noopener">
          <div>
            <i data-lucide="download-cloud"></i>
            <strong>SaveFrom Web Downloader</strong>
          </div>
          <span>Fast Mirror</span>
        </a>
        <button class="dl-option-btn" style="background: rgba(99, 102, 241, 0.1); border-color: rgba(99, 102, 241, 0.3); color: #818cf8;" onclick="app.copyToClipboard('${ytUrl}')">
          <div>
            <i data-lucide="copy"></i>
            <span>Copy YouTube Link</span>
          </div>
          <span>Copy</span>
        </button>
      `;
      this.renderIcons();
      return;
    }

    // If Anime Series / Embed Stream
    const currentServer = this.currentItem.episodesList?.[this.currentEpisode - 1]?.servers?.[this.currentServerIndex];
    const streamUrl = currentServer?.url || this.embedIframe.src;

    optionsList.innerHTML = `
      <a href="${streamUrl}" class="dl-option-btn" target="_blank" rel="noopener">
        <div>
          <i data-lucide="external-link"></i>
          <strong>Open Direct Stream Source (Full Screen)</strong>
        </div>
        <span>Open Source</span>
      </a>
      <a href="https://cobalt.tools/#${encodeURIComponent(streamUrl)}" class="dl-option-btn" target="_blank" rel="noopener">
        <div>
          <i data-lucide="download"></i>
          <strong>Extract Stream with Cobalt Downloader</strong>
        </div>
        <span>Save Stream</span>
      </a>
      <button class="dl-option-btn" style="background: rgba(99, 102, 241, 0.1); border-color: rgba(99, 102, 241, 0.3); color: #818cf8;" onclick="app.copyToClipboard('${streamUrl}')">
        <div>
          <i data-lucide="copy"></i>
          <span>Copy Stream Source Link</span>
        </div>
        <span>Copy</span>
      </button>
    `;
    this.renderIcons();
  }

  closeDownloadModal() {
    this.downloadModal.classList.add('hidden');
  }

  cleanFilename(str) {
    return (str || 'video').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
  }

  copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(() => {
      this.showToast('Link copied to clipboard!', 'clipboard-check');
    }).catch(() => {
      this.showToast('Could not copy link', 'alert-circle');
    });
  }

  // -------------------------------------------------------------
  // Watchlist & History Persistence
  // -------------------------------------------------------------
  toggleWatchlistFromPlayer() {
    if (!this.currentItem) return;
    this.toggleWatchlist(this.currentItem);
  }

  toggleWatchlistFromHero(id, encodedObj) {
    try {
      const item = JSON.parse(decodeURIComponent(encodedObj));
      this.toggleWatchlist(item);
    } catch (e) {
      console.warn('Error parsing hero item:', e);
    }
  }

  toggleWatchlist(item) {
    const idx = this.watchlist.findIndex(w => w.id === item.id);
    if (idx >= 0) {
      this.watchlist.splice(idx, 1);
      this.showToast('Removed from Watchlist', 'bookmark-minus');
      this.updateBookmarkButtonState(false);
    } else {
      this.watchlist.unshift({
        id: item.id,
        title: item.title,
        coverImage: { large: item.poster || item.coverImage?.large },
        format: item.format || 'ANIME',
        averageScore: item.averageScore,
        type: item.type || 'anime'
      });
      this.showToast('Saved to Watchlist', 'bookmark-check');
      this.updateBookmarkButtonState(true);
    }

    this.saveStorage('animestream_watchlist', this.watchlist);
    this.updateWatchlistCounter();

    if (this.currentTab === 'watchlist') {
      this.showWatchlist();
    }
  }

  updateBookmarkButtonState(isSaved) {
    if (!this.playerBookmarkBtn) return;
    if (isSaved) {
      this.playerBookmarkBtn.style.color = '#38bdf8';
      this.playerBookmarkBtn.querySelector('.btn-label').textContent = 'Saved';
    } else {
      this.playerBookmarkBtn.style.color = '';
      this.playerBookmarkBtn.querySelector('.btn-label').textContent = 'Save';
    }
  }

  updateWatchlistCounter() {
    const counter = document.getElementById('watchlistCount');
    if (counter) counter.textContent = this.watchlist.length;
  }

  showWatchlist() {
    this.sectionTitle.textContent = 'My Saved Watchlist';
    this.sectionCount.textContent = `${this.watchlist.length} Saved`;
    if (this.watchlist.length === 0) {
      this.mediaGrid.innerHTML = '';
      this.emptyState.classList.remove('hidden');
      this.emptyState.querySelector('h3').textContent = 'Your Watchlist is empty';
      this.emptyState.querySelector('p').textContent = 'Bookmark your favorite anime series, cartoons, or movies to watch anytime.';
      return;
    }
    this.renderMediaGrid(this.watchlist);
  }

  saveHistory() {
    if (!this.currentItem) return;
    this.history[this.currentItem.id] = {
      id: this.currentItem.id,
      title: this.currentItem.title,
      episode: this.currentEpisode,
      timestamp: Date.now()
    };
    this.saveStorage('animestream_history', this.history);
  }

  // -------------------------------------------------------------
  // Dedicated YouTube Hub
  // -------------------------------------------------------------
  showYouTubeHub() {
    this.mediaGrid.classList.add('hidden');
    this.emptyState.classList.add('hidden');
    this.youtubeHubSection.classList.remove('hidden');
    this.loadCuratedYouTube('tom-jerry');
  }

  async loadCuratedYouTube(category, button = null) {
    if (button) {
      document.querySelectorAll('.yt-tab-btn').forEach(btn => btn.classList.remove('active'));
      button.classList.add('active');
    }

    this.youtubeGrid.innerHTML = '<div class="card-skeleton skeleton"></div><div class="card-skeleton skeleton"></div><div class="card-skeleton skeleton"></div><div class="card-skeleton skeleton"></div>';

    try {
      const res = await fetch(`/api/youtube/cartoons?category=${category}`);
      const json = await res.json();
      if (json.success && json.data) {
        this.renderYouTubeGrid(json.data);
      }
    } catch (e) {
      console.warn('YouTube load failed:', e);
    }
  }

  async searchYouTubeDirect() {
    const q = document.getElementById('ytSearchInput').value.trim();
    if (!q) return;

    this.youtubeGrid.innerHTML = '<div class="card-skeleton skeleton"></div><div class="card-skeleton skeleton"></div><div class="card-skeleton skeleton"></div><div class="card-skeleton skeleton"></div>';

    try {
      const res = await fetch(`/api/youtube/search?q=${encodeURIComponent(q)}`);
      const json = await res.json();
      if (json.success && json.data) {
        this.renderYouTubeGrid(json.data);
      }
    } catch (e) {
      console.warn('YouTube search failed:', e);
    }
  }

  renderYouTubeGrid(videos) {
    this.youtubeGrid.innerHTML = '';
    videos.forEach(v => {
      const card = document.createElement('div');
      card.className = 'media-card is-video';
      card.innerHTML = `
        <div class="card-poster-wrapper">
          <img class="card-poster" src="${v.thumbnail}" alt="${v.title}" loading="lazy">
          <div class="card-badges">
            <span class="badge-format" style="background: rgba(220, 38, 38, 0.9);">YouTube</span>
          </div>
          <span class="badge-duration">${v.duration}</span>
          <div class="play-hover-btn">
            <div class="play-circle" style="background: #dc2626;">
              <i data-lucide="play"></i>
            </div>
          </div>
        </div>
        <div class="card-info">
          <h3 class="card-title" title="${v.title}">${v.title}</h3>
          <p class="card-sub">${v.channel} • ${v.views}</p>
        </div>
      `;
      card.addEventListener('click', () => {
        this.playYouTubeVideo(v);
      });
      this.youtubeGrid.appendChild(card);
    });
    this.renderIcons();
  }

  // -------------------------------------------------------------
  // Search Engine (Debounced)
  // -------------------------------------------------------------
  toggleSearch() {
    this.searchDrawer.classList.toggle('hidden');
    if (!this.searchDrawer.classList.contains('hidden')) {
      this.globalSearchInput.focus();
    }
    this.renderIcons();
  }

  setSearchScope(scope, button) {
    this.searchScope = scope;
    document.querySelectorAll('.search-chips .chip').forEach(c => c.classList.remove('active'));
    button.classList.add('active');
    if (this.globalSearchInput.value.trim()) {
      this.executeSearch(this.globalSearchInput.value.trim());
    }
  }

  handleSearchInput(value) {
    const val = value.trim();
    this.clearSearchBtn.classList.toggle('hidden', !val);

    if (this.searchTimeout) clearTimeout(this.searchTimeout);
    if (!val) {
      this.switchCategory(this.currentCategory);
      return;
    }

    this.searchTimeout = setTimeout(() => {
      this.executeSearch(val);
    }, 350);
  }

  clearSearch() {
    this.globalSearchInput.value = '';
    this.clearSearchBtn.classList.add('hidden');
    this.switchCategory(this.currentCategory);
  }

  async executeSearch(query) {
    this.renderSkeletons();
    this.sectionTitle.textContent = `Search results for "${query}"`;
    this.sectionCount.textContent = 'Searching...';

    // If YouTube scope selected
    if (this.searchScope === 'youtube') {
      try {
        const res = await fetch(`/api/youtube/search?q=${encodeURIComponent(query)}`);
        const json = await res.json();
        if (json.success && json.data) {
          this.renderMediaGrid(json.data);
          this.sectionCount.textContent = `${json.data.length} Results`;
        }
      } catch (e) {
        console.warn('YouTube search failed:', e);
      }
      return;
    }

    // AniList Anime / Cartoons / Movies search
    try {
      this.currentSearchQuery = query;
      let url = `/api/anime/search?q=${encodeURIComponent(query)}&page=${page}&limit=24`;
      if (this.searchScope === 'movies') url += '&format=MOVIE';
      if (this.searchScope === 'cartoons') url += '&genre=Comedy';

      const res = await fetch(url);
      const json = await res.json();
      if (json.success && json.data && json.data.length > 0) {
        this.renderMediaGrid(json.data);
        this.sectionCount.textContent = `${json.data.length} Results`;
        if (json.pageInfo) {
          this.updatePagination(json.pageInfo);
        }
      } else {
        // Fallback search to YouTube cartoons/anime
        const ytRes = await fetch(`/api/youtube/search?q=${encodeURIComponent(query)}`);
        const ytJson = await ytRes.json();
        if (ytJson.success && ytJson.data && ytJson.data.length > 0) {
          this.renderMediaGrid(ytJson.data);
          this.sectionTitle.textContent = `YouTube Results for "${query}"`;
          this.sectionCount.textContent = `${ytJson.data.length} Videos`;
          this.paginationBar.classList.add('hidden');
        } else {
          this.mediaGrid.innerHTML = '';
          this.emptyState.classList.remove('hidden');
          this.sectionCount.textContent = '0 Results';
          this.paginationBar.classList.add('hidden');
        }
      }
    } catch (e) {
      console.warn('Search failed:', e);
      this.mediaGrid.innerHTML = '';
      this.emptyState.classList.remove('hidden');
      this.paginationBar.classList.add('hidden');
    }
  }

  handleGenreFilter(genre) {
    if (!genre) {
      this.switchCategory(this.currentCategory, null, 1);
      return;
    }
    this.renderSkeletons();
    this.sectionTitle.textContent = `${genre} Animations & Series`;

    fetch(`/api/anime/search?genre=${encodeURIComponent(genre)}&page=1&limit=24`)
      .then(res => res.json())
      .then(json => {
        if (json.success && json.data) {
          this.renderMediaGrid(json.data);
          this.sectionCount.textContent = `${json.data.length} Titles`;
          if (json.pageInfo) {
            this.updatePagination(json.pageInfo);
          }
        }
      })
      .catch(() => {
        this.switchCategory(this.currentCategory, null, 1);
      });
  }

  // -------------------------------------------------------------
  // Universal Pagination Controller
  // -------------------------------------------------------------
  updatePagination(pageInfo) {
    if (!pageInfo) {
      this.paginationBar.classList.add('hidden');
      return;
    }

    this.currentPage = pageInfo.currentPage || 1;
    this.totalPages = pageInfo.lastPage || (pageInfo.hasNextPage ? this.currentPage + 1 : this.currentPage);
    this.hasNextPage = !!pageInfo.hasNextPage;
    this.totalItems = pageInfo.total || 0;

    if (this.totalPages <= 1) {
      this.paginationBar.classList.add('hidden');
      return;
    }

    this.paginationBar.classList.remove('hidden');
    this.currentPageNum.textContent = this.currentPage;
    this.totalPagesNum.textContent = this.totalPages;

    this.prevPageBtn.disabled = this.currentPage <= 1;
    this.nextPageBtn.disabled = !this.hasNextPage && this.currentPage >= this.totalPages;

    this.renderPageNumbers();
  }

  renderPageNumbers() {
    this.pageNumberButtons.innerHTML = '';
    const current = this.currentPage;
    const total = this.totalPages;

    const pages = [];
    if (total <= 7) {
      for (let i = 1; i <= total; i++) pages.push(i);
    } else {
      pages.push(1);
      if (current > 3) pages.push('...');
      
      const start = Math.max(2, current - 1);
      const end = Math.min(total - 1, current + 1);
      for (let i = start; i <= end; i++) {
        pages.push(i);
      }

      if (current < total - 2) pages.push('...');
      pages.push(total);
    }

    pages.forEach(p => {
      const btn = document.createElement('button');
      if (p === '...') {
        btn.className = 'page-num-btn dots';
        btn.textContent = '...';
      } else {
        btn.className = `page-num-btn ${p === current ? 'active' : ''}`;
        btn.textContent = p;
        btn.onclick = () => this.changePage(p);
      }
      this.pageNumberButtons.appendChild(btn);
    });
  }

  changePage(newPage) {
    if (newPage < 1 || (this.totalPages > 1 && newPage > this.totalPages)) return;
    this.currentPage = newPage;

    if (this.currentTab === 'directory') {
      this.loadDirectory(newPage);
    } else if (this.currentSearchQuery) {
      this.executeSearch(this.currentSearchQuery, newPage);
    } else {
      this.switchCategory(this.currentCategory, null, newPage);
    }

    // Scroll smoothly to top of grid
    const target = this.currentTab === 'directory' ? this.directorySection : document.querySelector('.section-heading-row') || this.mediaGrid;
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  goToPrevPage() {
    if (this.currentPage > 1) this.changePage(this.currentPage - 1);
  }

  goToNextPage() {
    this.changePage(this.currentPage + 1);
  }

  jumpToPage(val) {
    const p = parseInt(val);
    if (!isNaN(p) && p >= 1) {
      this.changePage(p);
      if (this.jumpPageInput) this.jumpPageInput.value = '';
    }
  }

  // -------------------------------------------------------------
  // Complete Anime Directory / Catalog System
  // -------------------------------------------------------------
  showDirectory(page = 1) {
    this.directorySection.classList.remove('hidden');
    this.mediaGrid.classList.add('hidden');
    this.emptyState.classList.add('hidden');
    this.loadDirectory(page);
  }

  async loadDirectory(page = 1) {
    this.currentPage = page;
    this.directoryContent.innerHTML = '<div class="card-skeleton skeleton"></div><div class="card-skeleton skeleton"></div><div class="card-skeleton skeleton"></div><div class="card-skeleton skeleton"></div>';

    let url = `/api/anime/directory?page=${page}&limit=24&sort=${this.dirFilter.sort}`;
    if (this.dirFilter.status) url += `&status=${this.dirFilter.status}`;
    if (this.dirFilter.format) url += `&format=${this.dirFilter.format}`;
    if (this.dirFilter.genre) url += `&genre=${encodeURIComponent(this.dirFilter.genre)}`;
    if (this.dirFilter.search) url += `&search=${encodeURIComponent(this.dirFilter.search)}`;

    try {
      const res = await fetch(url);
      const json = await res.json();
      if (json.success && json.data && json.data.length > 0) {
        this.dirItems = json.data;
        this.renderDirectoryContent(this.dirItems);
        this.updatePagination(json.pageInfo);
      } else {
        this.directoryContent.innerHTML = '<div class="empty-state"><i data-lucide="inbox"></i><h3>No anime found matching directory filters</h3><p>Try resetting filters or searching another title.</p></div>';
        this.paginationBar.classList.add('hidden');
        this.renderIcons();
      }
    } catch (e) {
      console.warn('Directory fetch failed:', e);
      this.directoryContent.innerHTML = '<div class="empty-state"><h3>Error loading directory</h3></div>';
      this.paginationBar.classList.add('hidden');
    }
  }

  setDirectoryView(mode) {
    this.directoryView = mode;
    this.viewModeGrid.classList.toggle('active', mode === 'grid');
    this.viewModeList.classList.toggle('active', mode === 'list');
    if (this.dirItems && this.dirItems.length > 0) {
      this.renderDirectoryContent(this.dirItems);
    }
  }

  renderDirectoryContent(items) {
    if (this.directoryView === 'list') {
      this.renderDirectoryTable(items);
    } else {
      this.renderDirectoryGrid(items);
    }
  }

  renderDirectoryGrid(items) {
    this.directoryContent.innerHTML = '<div class="media-grid" id="dirInnerGrid"></div>';
    const grid = document.getElementById('dirInnerGrid');

    items.forEach(item => {
      const card = document.createElement('div');
      card.className = 'media-card';
      const title = item.title?.english || item.title?.romaji || 'Untitled';
      const poster = item.coverImage?.large || item.coverImage?.extraLarge || 'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600';
      const format = item.format || 'TV';
      const score = item.averageScore ? `${item.averageScore}%` : null;
      const subInfo = item.genres?.slice(0, 2).join(' • ') || (item.seasonYear ? `${item.seasonYear}` : '');

      card.innerHTML = `
        <div class="card-poster-wrapper">
          <img class="card-poster" src="${poster}" alt="${title}" loading="lazy">
          <div class="card-badges">
            <span class="badge-format">${format}</span>
            ${score ? `<span class="badge-score"><i data-lucide="star"></i> ${score}</span>` : ''}
          </div>
          ${item.episodes ? `<span class="badge-ep">Ep ${item.episodes}</span>` : ''}
          <div class="play-hover-btn">
            <div class="play-circle"><i data-lucide="play"></i></div>
          </div>
        </div>
        <div class="card-info">
          <h3 class="card-title" title="${title}">${title}</h3>
          <p class="card-sub">${subInfo}</p>
        </div>
      `;
      card.addEventListener('click', () => {
        this.playAnime(item.id);
      });
      grid.appendChild(card);
    });
    this.renderIcons();
  }

  renderDirectoryTable(items) {
    let rowsHtml = '';
    items.forEach((item, idx) => {
      const title = item.title?.english || item.title?.romaji || 'Untitled';
      const poster = item.coverImage?.medium || item.coverImage?.large || '';
      const format = item.format || 'TV';
      const episodes = item.episodes ? `${item.episodes} Ep` : 'Ongoing';
      const score = item.averageScore ? `${item.averageScore}%` : 'N/A';
      const statusClass = item.status === 'RELEASING' ? 'badge-status-ongoing' : 'badge-status-finished';
      const statusLabel = item.status === 'RELEASING' ? 'Ongoing' : 'Completed';
      const genres = item.genres?.slice(0, 3).join(', ') || 'Anime';

      rowsHtml += `
        <tr class="dir-table-row" onclick="app.playAnime(${item.id})">
          <td style="color: var(--text-muted); font-weight: 600;">${(this.currentPage - 1) * 24 + idx + 1}</td>
          <td class="dir-thumb-cell">
            <img class="dir-thumb-img" src="${poster}" alt="${title}" loading="lazy">
          </td>
          <td class="dir-title-cell">
            <span class="dir-main-title">${title}</span>
            <span class="dir-sub-genres">${genres}</span>
          </td>
          <td><span class="badge-format">${format}</span> <small style="color: var(--text-muted); margin-left: 4px;">${episodes}</small></td>
          <td style="color: #34d399; font-weight: 700;"><i data-lucide="star" style="width: 13px; height: 13px; display: inline-block; vertical-align: middle;"></i> ${score}</td>
          <td><span class="${statusClass}">${statusLabel}</span></td>
          <td>
            <button class="dir-play-btn" onclick="event.stopPropagation(); app.playAnime(${item.id})">
              <i data-lucide="play"></i> Watch
            </button>
          </td>
        </tr>
      `;
    });

    this.directoryContent.innerHTML = `
      <div class="directory-table-wrap">
        <table class="directory-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Poster</th>
              <th>Title</th>
              <th>Format</th>
              <th>Score</th>
              <th>Status</th>
              <th>Play</th>
            </tr>
          </thead>
          <tbody>
            ${rowsHtml}
          </tbody>
        </table>
      </div>
    `;
    this.renderIcons();
  }

  handleDirFilter() {
    this.dirFilter.sort = this.dirSort.value;
    this.dirFilter.status = this.dirStatus.value;
    this.dirFilter.format = this.dirFormat.value;
    this.dirFilter.genre = this.dirGenre.value;
    this.loadDirectory(1);
  }

  handleDirSearch(val) {
    if (this.dirSearchTimeout) clearTimeout(this.dirSearchTimeout);
    this.dirSearchTimeout = setTimeout(() => {
      this.dirFilter.search = val.trim();
      this.loadDirectory(1);
    }, 350);
  }
}

// Instantiate application on DOM ready
document.addEventListener('DOMContentLoaded', () => {
  window.app = new AnimeStreamApp();
});
