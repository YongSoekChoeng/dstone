package net.dstone.boot.ai.vo;

/**
 * /ai/sqlconvert/convert.do 요청 바디. 변환하고 싶은 오라클 SQL 한 문장을 그대로 담는다.
 */
public record SqlConvertRequest(String originalSql) {
}
