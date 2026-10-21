import forge from 'node-forge'
import request from '@/utils/request'

/**
 * 密码上行传输加密（RSA-OAEP）。
 *
 * HTTP 内网环境下（无 WebCrypto 安全上下文），dbPass/ftpPass 在提交前用后端
 * 下发的 RSA-2048 公钥做 OAEP(SHA-256) 加密，报文中不再出现明文；
 * 后端以 yml 配对的私钥解密后继续走 AES-256-GCM 落库流程。
 * 公钥仅首次加密前拉取一次，模块级缓存（页面生命周期内复用）。
 */

interface PublicKeyResponse {
  publicKey: string
}

let cachedKey: forge.pki.rsa.PublicKey | null = null
let pending: Promise<forge.pki.rsa.PublicKey> | null = null

/** 拉取并缓存公钥（SPKI DER Base64 → forge PublicKey），并发调用共享同一请求。 */
function loadPublicKey(): Promise<forge.pki.rsa.PublicKey> {
  if (cachedKey) return Promise.resolve(cachedKey)
  if (!pending) {
    pending = request
      .get<unknown, PublicKeyResponse>('/crypto/public-key')
      .then(res => {
        const der = forge.util.decode64(res.publicKey)
        const asn1 = forge.asn1.fromDer(der)
        cachedKey = forge.pki.publicKeyFromAsn1(asn1) as forge.pki.rsa.PublicKey
        return cachedKey
      })
      .catch(err => {
        // 失败不缓存，允许下次操作重试拉取
        pending = null
        throw err
      })
  }
  return pending
}

/**
 * 加密密码明文 → Base64(RSA-OAEP 密文)。
 * 参数对齐后端 RsaTransitCrypto：主摘要 SHA-256 + MGF1-SHA-256。
 */
export async function encryptPass(plain: string): Promise<string> {
  const key = await loadPublicKey()
  // createBuffer(plain,'utf8').getBytes() → 按 UTF-8 编码的原始字节串（与后端 UTF-8 取字节对齐）
  const encrypted = key.encrypt(forge.util.createBuffer(plain, 'utf8').getBytes(), 'RSA-OAEP', {
    md: forge.md.sha256.create(),
    mgf1: { md: forge.md.sha256.create() },
  })
  return forge.util.encode64(encrypted)
}

/** 空值透传：留空表示不修改密码（后端保留原密文语义），非空才加密。 */
export async function encryptPassOrEmpty(plain?: string | null): Promise<string> {
  if (plain === undefined || plain === null || plain === '') return ''
  return encryptPass(plain)
}
