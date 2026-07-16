/**
 * Phone registration is only for the Chinese locale market.
 * Other locales (en / id / vi / th) must use email.
 */
export function isPhoneRegisterAllowed(locale: string): boolean {
  return locale === 'zh-CN'
}
