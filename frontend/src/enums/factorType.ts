/**
 * 认证因子 SPI 类型枚举 (FactorType)
 * <p>
 * 对应后端 com.example.authengine.domain.FactorType
 */
export enum FactorType {
  /** 账户口令密码单向哈希核验 (BCrypt) */
  PASSWORD = 'PASSWORD',
  /** 短信一次性动态口令验证码 (SMS_OTP) */
  SMS_OTP = 'SMS_OTP',
  /** 移动 APP 扫码跨端反向授权 (APP_QR_APPROVAL) */
  APP_QR_APPROVAL = 'APP_QR_APPROVAL',
  /** WebAuthn 硬件通行密钥公钥核验 (PASSKEY) */
  PASSKEY = 'PASSKEY',
}

export interface FactorTypeMeta {
  code: FactorType;
  name: string;
  icon: string;
  color: string;
  description: string;
  badge: string;
}

export const FACTOR_TYPE_META: Record<FactorType, FactorTypeMeta> = {
  [FactorType.PASSWORD]: {
    code: FactorType.PASSWORD,
    name: '密码认证',
    icon: '🔑',
    color: 'indigo',
    description: '密码单向哈希安全核验',
    badge: 'BCrypt',
  },
  [FactorType.SMS_OTP]: {
    code: FactorType.SMS_OTP,
    name: '短信验证码',
    icon: '💬',
    color: 'amber',
    description: '时效性一次性动态短信码',
    badge: '120s TTL',
  },
  [FactorType.APP_QR_APPROVAL]: {
    code: FactorType.APP_QR_APPROVAL,
    name: 'APP 扫码授权',
    icon: '📲',
    color: 'purple',
    description: '移动端安全令扫码授权',
    badge: 'Ticket 90s',
  },
  [FactorType.PASSKEY]: {
    code: FactorType.PASSKEY,
    name: '通行密钥',
    icon: '🛡️',
    color: 'rose',
    description: 'WebAuthn 生物特征与硬件公钥',
    badge: 'FIDO2',
  },
};
