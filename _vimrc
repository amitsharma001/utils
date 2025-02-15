" Get out of VI's compatible mode.
set nocompatible

" Sets how many lines of history VIM has to remember
set history=400

" Enable filetype plugin
filetype plugin on
filetype indent on

" Set a usable font and screen size
if has("gui_running")
  if has("gui_gtk2")
    set guifont=Inconsolata\ 12
  elseif has("gui_macvim")
    set guifont=Menlo\ Regular:h12
  elseif has("gui_win32")
    set guifont=Consolas:h11:cANSI
  endif
  set lines=45
  set columns=140
endif

" Always show current position
set ruler
set showcmd
set wildmenu

"The commandbar is 2 high
"set cmdheight=2

" Show line numbers
set nu

" Show matching patterns as search string is typed
set incsearch

" Highlight matching text
set hlsearch

" No sound on errors.
set noerrorbells
set novisualbell

" Show matching brackets and how many tenths of a second to blink
set showmatch
set matchtime=2

" Turn backup off
set backup
set backupdir=~/.editorBCKP  " create all backups in one directory
set dir=~/.editorBCKP  " create all swap files in one directory

syntax on

" Smart tabs
set expandtab
set smarttab
set shiftwidth=4
set autoindent
set hidden
set path=$PWD/**
