# ~/.bashrc — woderOS draft (Kali-style, for bash sessions)
# NOTE: ye draft hai. Apply-time par Kali ki asli default .bashrc
# (kali-defaults, etc/skel/.bashrc) se milan karke pakka kiya jayega.
# Default shell woderOS me zsh hoga; ye unke liye hai jo bash kholein.

# Sirf interactive shell ke liye
case $- in
    *i*) ;;
      *) return;;
esac

HISTCONTROL=ignoreboth
shopt -s histappend
HISTSIZE=1000
HISTFILESIZE=2000
shopt -s checkwinsize

# Kali style prompt (do-line, PROMPT_ALTERNATIVE=twoline)
PROMPT_ALTERNATIVE=twoline
prompt_symbol=㉿
case "$PROMPT_ALTERNATIVE" in
    twoline)
        PS1='\[\033[01;32m\]┌──(\[\033[01;34m\]\u'"$prompt_symbol"'\h\[\033[01;32m\])-[\[\033[00m\]\w\[\033[01;32m\]]\n\[\033[01;32m\]└─\[\033[01;34m\]\$\[\033[00m\] '
        ;;
    oneline)
        PS1='\[\033[01;34m\]\u@\h\[\033[00m\]:\[\033[01;32m\]\w\[\033[00m\]\$ '
        ;;
esac
unset prompt_symbol

# Rang support (Termux raaste)
if [ -x $PREFIX/bin/dircolors ]; then
    test -r ~/.dircolors && eval "$(dircolors -b ~/.dircolors)" || eval "$(dircolors -b)"
    alias ls='ls --color=auto'
    alias grep='grep --color=auto'
    alias fgrep='fgrep --color=auto'
    alias egrep='egrep --color=auto'
    alias diff='diff --color=auto'
    alias ip='ip --color=auto'
fi

alias ll='ls -l'
alias la='ls -A'
alias l='ls -CF'

# --- woderOS one-time Kali setup -------------------------------------------
# Default shell is zsh (login reads ~/.termux/shell). Until zsh is installed,
# login falls back to this bash — so on the very first launch we install
# zsh + its Kali plugins once, then switch straight into zsh.
if [ ! -x "$PREFIX/bin/zsh" ] && [ ! -f "$HOME/.termux/.woderos_kali_done" ] \
   && [ ! -f "$TMPDIR/.woderos_kali_tried" ]; then
    touch "$TMPDIR/.woderos_kali_tried" 2>/dev/null
    echo ""
    echo "woderOS: Kali wala mahaul taiyaar ho raha hai — zsh install ho raha hai, ek minute..."
    echo ""
    if pkg install -y zsh zsh-syntax-highlighting zsh-autosuggestions; then
        touch "$HOME/.termux/.woderos_kali_done" 2>/dev/null
        echo ""
        echo "woderOS: ho gaya! Ab Kali wala zsh khul raha hai..."
        exec zsh -l
    else
        echo ""
        echo "woderOS: zsh install nahi ho paya (net check karo). Agli baar khud phir koshish hogi."
        echo ""
    fi
fi
