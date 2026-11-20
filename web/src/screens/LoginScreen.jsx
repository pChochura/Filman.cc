import React, { useEffect, useState } from 'react';
import { t } from '../i18n/index.js';
import { TmdbFeed, fetchFeedPage } from '../data/api/tmdbClient.js';
import { loginAccount, registerAccount } from '../data/sync/syncManager.js';
import FilmanButton from '../components/FilmanButton.jsx';
import TabRowSection from '../components/sections/TabRowSection.jsx';
import { IconLogin } from '../components/Icons.jsx';

export default function LoginScreen({ onSuccess, onBack }) {
  const [tab, setTab] = useState(0); // 0 = login, 1 = register
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);
  const [backdrops, setBackdrops] = useState([]);
  const [slideIdx, setSlideIdx] = useState(0);

  useEffect(() => {
    fetchFeedPage(TmdbFeed.TRENDING_ALL_DAY, 1).then((res) => {
      const urls = (res.featuredItems || []).map((m) => m.backgroundUrl).filter(Boolean);
      setBackdrops(urls);
    });
  }, []);

  useEffect(() => {
    if (backdrops.length <= 1) return undefined;
    const timer = setInterval(() => {
      setSlideIdx((prev) => (prev + 1) % backdrops.length);
    }, 5000);
    return () => clearInterval(timer);
  }, [backdrops.length]);

  const handleSubmit = async (e) => {
    e?.preventDefault?.();
    const u = username.trim();
    const p = password.trim();
    if (!u || !p) {
      setErrorMessage(t('login_error_invalid_credentials'));
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);
    try {
      if (tab === 0) {
        await loginAccount(u, p);
      } else {
        await registerAccount(u, p);
      }
      onSuccess?.();
    } catch (err) {
      setErrorMessage(err?.message || t('login_error_server'));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onBack?.();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onBack]);

  const currentBackdrop = backdrops[slideIdx] || null;

  return (
    <div className="login-screen">
      {currentBackdrop && (
        <img
          key={currentBackdrop}
          src={currentBackdrop}
          alt=""
          className="login-backdrop-img"
        />
      )}
      <div className="hero-scrim" />

      <form className="login-card" onSubmit={handleSubmit}>
        <h1 className="type-headline-medium">{t('app_name')}</h1>
        <p className="type-body-medium" style={{ color: 'var(--color-on-surface-variant)' }}>
          {t('login_description')}
        </p>

        <TabRowSection
          tabs={[
            { id: 0, label: t('login_login_tab') },
            { id: 1, label: t('login_register_tab') },
          ]}
          selectedId={tab}
          onSelectTab={(id) => {
            setTab(id);
            setErrorMessage(null);
          }}
        />

        {errorMessage && (
          <div className="login-error-banner type-body-small" role="alert">
            {errorMessage}
          </div>
        )}

        <input
          type="text"
          value={username}
          placeholder={t('login_username')}
          className="login-input"
          autoComplete="username"
          data-focusable="true"
          onChange={(e) => setUsername(e.target.value)}
        />

        <input
          type="password"
          value={password}
          placeholder={t('login_password')}
          className="login-input"
          autoComplete={tab === 0 ? 'current-password' : 'new-password'}
          data-focusable="true"
          onChange={(e) => setPassword(e.target.value)}
        />

        <FilmanButton
          text={tab === 0 ? t('login_confirm') : t('login_register_confirm')}
          icon={<IconLogin size={20} />}
          fullWidth={true}
          isLoading={isLoading}
          onClick={handleSubmit}
        />
      </form>
    </div>
  );
}
