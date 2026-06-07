import React from 'react';
import { useTranslation } from 'react-i18next';
import { AppLogo } from '../components/AppBrand';
import { IconGuest, IconGoogle, IconKeycloak, IconSkelOAuth } from './LoginIcons';

export type LoginOptionId = 'guest' | 'skel' | 'keycloak' | 'google';

interface LoginOption {
  id: LoginOptionId;
  labelKey: string;
  descKey: string;
  icon: React.ReactNode;
}

interface LoginScreenProps {
  appName: string;
  logoUrl: string;
  onSelect: (option: LoginOptionId) => void;
}

function LoginOptionButton({
  icon,
  label,
  description,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  description: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg border border-border bg-card
        hover:bg-muted hover:border-blue-300 transition-colors text-left group"
    >
      <span className="w-9 h-9 flex items-center justify-center shrink-0 rounded-md bg-muted group-hover:bg-background">
        {icon}
      </span>
      <span className="flex-1 min-w-0">
        <span className="block text-sm text-foreground">{label}</span>
        <span className="block text-xs text-muted-foreground truncate">{description}</span>
      </span>
    </button>
  );
}

export function LoginScreen({ appName, logoUrl, onSelect }: LoginScreenProps) {
  const { t } = useTranslation();

  const options: LoginOption[] = [
    {
      id: 'guest',
      labelKey: 'login.guest',
      descKey: 'login.guestDesc',
      icon: <IconGuest size={22} className="text-muted-foreground" />,
    },
    {
      id: 'skel',
      labelKey: 'login.skelOAuth',
      descKey: 'login.skelOAuthDesc',
      icon: <IconSkelOAuth size={22} />,
    },
    {
      id: 'keycloak',
      labelKey: 'login.keycloak',
      descKey: 'login.keycloakDesc',
      icon: <IconKeycloak size={22} />,
    },
    {
      id: 'google',
      labelKey: 'login.googleOAuth',
      descKey: 'login.googleOAuthDesc',
      icon: <IconGoogle size={22} />,
    },
  ];

  return (
    <div className="flex items-center justify-center min-h-screen bg-background">
      <div className="bg-card border border-border rounded-lg shadow p-8 max-w-sm w-full mx-4">
        <div className="flex flex-col items-center text-center mb-6">
          <div className="flex justify-center mb-3 text-foreground">
            <AppLogo logoUrl={logoUrl} size={48} iconClassName="text-foreground" />
          </div>
          <h1 className="text-xl text-foreground mb-1">{appName}</h1>
          <p className="text-muted-foreground text-sm">{t('login.subtitle')}</p>
        </div>

        <div className="flex flex-col gap-2">
          {options.map((opt) => (
            <LoginOptionButton
              key={opt.id}
              icon={opt.icon}
              label={t(opt.labelKey)}
              description={t(opt.descKey)}
              onClick={() => onSelect(opt.id)}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
