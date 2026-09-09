package net.dstone.ai.governance.auth;

/**
 * dstone.ai.governance.auth.keys(YAML 시퀀스) 항목 하나. key는 DB 패스워드/API 키와 동일한 컨벤션대로
 * ENC(...)로 암호화해 넣는 걸 권장한다(Jasypt 복호화는 PropertySource 레벨에서 일어나므로
 * {@link net.dstone.ai.governance.auth.ApiKeyProperties}가 Binder로 읽어도 그대로 적용된다).
 */
public record ApiKeyEntry(String key, String caller) {
}
