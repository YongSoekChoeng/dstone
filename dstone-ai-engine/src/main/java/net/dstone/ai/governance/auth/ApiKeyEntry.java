package net.dstone.ai.governance.auth;

/**
 * dstone.ai.governance.auth.keys(YAML 시퀀스) 항목 하나다. key는 DB 패스워드와 같은 방식으로
 * ENC(...)로 암호화해서 넣는 걸 권장한다 - Jasypt 복호화는 PropertySource 레벨에서 일어나기 때문에,
 * ApiKeyProperties가 Binder로 읽어도 똑같이 복호화된 값을 받는다.
 */
public record ApiKeyEntry(String key, String caller) {
}
