package net.dstone.batch.common.items;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.stereotype.Component;

/**
 * ItemReader 공통 베이스 클래스.
 * <pre>
 * Step 재시작(restart) 시 Spring Batch는 이전 실행에서 STOPPED/FAILED 된 Step의 JobExecution만 이어받을 뿐,
 * "몇번째 항목까지 읽었는지"는 ItemReader가 ItemStream(open/update/close)을 통해 스스로 ExecutionContext에
 * 저장해두지 않으면 알 수 없다. 이 클래스가 그 체크포인트 저장/복원을 공통으로 처리한다.
 *
 * 구현체는 read() 대신 doRead()를 오버라이드한다. doRead()는 매 Step 시작(신규/재시작 불문)마다
 * 항상 처음 데이터부터 순서대로, 결정적(deterministic)으로 반환하도록 작성해야 한다.
 * (이 클래스가 재시작 시 이전 실행에서 이미 커밋완료된 개수만큼 doRead()를 내부적으로 미리 호출해
 * 건너뛰어 줌으로써, 중복 없이 이어서 처리되도록 지원한다.)
 * </pre>
 */
@Component
@StepScope
public abstract class AbstractItemReader<I> extends AbstractItem implements ItemStreamReader<I> {

	/** ExecutionContext에 저장되는, 지금까지 실제로 doRead()를 호출해 반환받은(=커밋대상이 된) 누적 항목수 키 */
	private static final String READ_COUNT_KEY = "dstone.reader.readCount";

	/** 이번 Step 실행 동안 doRead()를 호출해 반환받은 누적 항목수(스킵구간 포함) */
	private int readCount = 0;
	/** 재시작 시 이전 실행에서 이미 커밋완료되어, 이번 실행에서 조용히 건너뛰어야 할 항목수 */
	private int skipCount = 0;

	/**
	 * 실제 데이터 조회 로직. read() 에서 자동으로 호출됨.
	 * @return 더 이상 읽을 데이터가 없으면 null
	 */
	protected abstract I doRead() throws Exception;

	@Override
	public final synchronized I read() throws Exception {
		// 재시작으로 인해 이전 실행에서 이미 커밋완료된 항목들은 조용히 건너뛴다.
		while (readCount < skipCount) {
			I skipped = doRead();
			if (skipped == null) {
				// 원본 데이터가 이전 실행 시점보다 줄어드는 등 비정상 상황 - 더 건너뛸 데이터가 없음
				return null;
			}
			readCount++;
		}
		I item = doRead();
		if (item != null) {
			readCount++;
		}
		return item;
	}

	@Override
	public void open(ExecutionContext executionContext) throws ItemStreamException {
		skipCount = executionContext.getInt(READ_COUNT_KEY, 0);
		readCount = 0;
	}

	@Override
	public synchronized void update(ExecutionContext executionContext) throws ItemStreamException {
		executionContext.putInt(READ_COUNT_KEY, readCount);
	}

	@Override
	public void close() throws ItemStreamException {

	}

}
