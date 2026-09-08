/**
 * 문서 적재/청킹(Phase 2). {@link net.dstone.ai.rag.ingest.DocumentIngestService}가
 * Tika로 원문을 추출하고(PDF/DOCX/PPTX/HTML/TXT 등) TokenTextSplitter로 청크를 나눠
 * VectorStore(pgvector)에 저장한다. dstone.ai.rag.enabled=true일 때만 빈이 활성화된다.
 */
package net.dstone.ai.rag.ingest;
