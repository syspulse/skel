import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { JsonViewer } from '../components/JsonViewer';
import { IconCopy } from '../components/Icons';
import { UserAvatar } from './UserAvatar';
import { useUserProfile } from './useUserProfile';
import { useAvatarUrl } from './useAvatarUrl';
import type { AuthType } from './userProfile';
import { useOwnerSettings, resolveOidExtract } from '../settings/OwnerContext';

const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED !== 'false';

function authTypeLabel(t: (key: string) => string, authType: AuthType): string {
  switch (authType) {
    case 'keycloak':
      return t('settings.keycloakAuth');
    case 'skel':
      return t('login.skelOAuth');
    case 'google':
      return t('login.googleOAuth');
    case 'guest':
      return t('settings.noAuth');
    default:
      return authType;
  }
}

function FieldRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex gap-3 text-xs py-1 border-b border-border last:border-0">
      <div className="w-28 shrink-0 text-muted-foreground">{label}</div>
      <div className="flex-1 text-foreground break-all">{value ?? '—'}</div>
    </div>
  );
}

function JwtRawField({ token }: { token: string }) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    if (!token) return;
    try {
      await navigator.clipboard.writeText(token);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard unavailable */
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <div className="text-xs text-muted-foreground">{t('settings.userProfile.jwtRaw')}</div>
        {token ? (
          <button
            type="button"
            onClick={handleCopy}
            className="inline-flex items-center gap-1 text-[10px] text-muted-foreground hover:text-foreground px-1.5 py-0.5 rounded border border-border hover:bg-muted transition-colors"
            aria-label={copied ? t('common.copied') : t('common.copy')}
            title={copied ? t('common.copied') : t('common.copy')}
          >
            <IconCopy size={12} />
            {copied ? t('common.copied') : t('common.copy')}
          </button>
        ) : null}
      </div>
      <textarea
        readOnly
        value={token}
        rows={4}
        placeholder=""
        className="w-full text-[11px] font-mono field px-2 py-1.5 bg-muted resize-y"
      />
    </div>
  );
}

export function UserProfileTab() {
  const { t } = useTranslation();
  const { profile, token, tokenParsed, userInfo, profileClaims, isLoading } = useUserProfile();
  const avatarUrl = useAvatarUrl();
  // default owner id for creating/starting WorkflowConfigs (persisted with settings)
  const { oid, setOid, oidExtract, setOidExtract } = useOwnerSettings();

  if (isLoading) {
    return (
      <div className="text-xs text-muted-foreground py-4">{t('settings.userProfile.loading')}</div>
    );
  }

  const displayName = profile?.name ?? '';
  const jwtToken = token ?? '';

  return (
    <div className="space-y-3">
      <div className="surface p-3">
        <div className="flex items-center gap-3 mb-3 pb-3 border-b border-border">
          <UserAvatar
            avatarUrl={avatarUrl}
            name={displayName || '—'}
            size={48}
            className="border border-border bg-muted"
          />
          <div>
            <div className="text-sm font-medium text-foreground">{displayName || '—'}</div>
            {profile?.email ? (
              <div className="text-xs text-muted-foreground">{profile.email}</div>
            ) : null}
          </div>
        </div>

        <FieldRow label={t('settings.userProfile.id')} value={profile?.id} />
        <FieldRow label={t('settings.user')} value={displayName || undefined} />
        <FieldRow label={t('settings.email')} value={profile?.email} />
        {avatarUrl && (
          <FieldRow
            label={t('settings.userProfile.avatar')}
            value={
              <a href={avatarUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">
                {avatarUrl}
              </a>
            }
          />
        )}
        <FieldRow
          label={t('settings.userProfile.authType')}
          value={profile ? authTypeLabel(t, profile.authType) : undefined}
        />
        {/* Owner (oid) default used when creating / starting a WorkflowConfig. Persisted with settings.
            - oid: the value (manual, default '0')
            - oid_extract: expression (e.g. {JWT}.tenantId). Press Enter to resolve it into `oid`
              (unresolved / no JWT -> '0'). More placeholders may be added later. */}
        <FieldRow
          label="oid"
          value={
            <input
              value={oid}
              onChange={(e) => setOid(e.target.value)}
              placeholder="0"
              className="field-compact w-40"
            />
          }
        />
        <FieldRow
          label="oid_extract"
          value={
            <input
              value={oidExtract}
              onChange={(e) => setOidExtract(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') setOid(resolveOidExtract(oidExtract, tokenParsed)); }}
              placeholder="{JWT}.tenantId"
              className="field-compact w-56 font-mono"
              title="Press Enter to resolve into oid"
            />
          }
        />
        {profile?.roles && profile.roles.length > 0 && (
          <FieldRow label={t('settings.roles')} value={profile.roles.join(', ')} />
        )}
      </div>

      {AUTH_ENABLED && (
        <div className="surface p-3 space-y-3">
          <JwtRawField token={jwtToken} />
          <div>
            <div className="text-xs text-muted-foreground mb-1.5">
              {t('settings.userProfile.jwtParsed')}
            </div>
            <JsonViewer value={tokenParsed ?? {}} />
          </div>
          {userInfo && Object.keys(userInfo).length > 0 && (
            <div>
              <div className="text-xs text-muted-foreground mb-1.5">
                {t('settings.userProfile.userInfo')}
              </div>
              <JsonViewer value={userInfo} />
            </div>
          )}
          {profileClaims && Object.keys(profileClaims).length > 0 && (
            <div>
              <div className="text-xs text-muted-foreground mb-1.5">
                {t('settings.userProfile.profileClaims')}
              </div>
              <JsonViewer value={profileClaims} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
