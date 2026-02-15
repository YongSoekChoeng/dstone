package net.dstone.batch.sample.jobs.job002;

import org.springframework.stereotype.Component;

import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.core.BaseJobConfig;
import net.dstone.batch.sample.jobs.job002.items.TableDeleteTasklet;
import net.dstone.batch.sample.jobs.job002.items.TableInsertTasklet;

/**
 * 테이블 SAMPLE_TEST 에 테스트데이터를 입력하는 Job.
 * <pre>
 * < 구성 >
 * 두 개의 Tasklet 으로 구현.
 * 01. 기존데이터 삭제 - Tasklet
 * 02. 신규데이터 입력 - Tasklet
 * 
 * < JobParameter >
 * 1. dataCnt : 생성데이터 갯수. 필수.
 * 2. gridSize : 병렬처리할 쓰레드 갯수. 옵션(기본값 1).
 * 
 * </pre>
 */
@Component
@AutoRegJob(name = "tableDataGenType01Job")
public class TableDataGenType01JobConfig extends BaseJobConfig {

	/********************************* 멤버변수 선언 시작 *********************************/
	private int dataCnt 		= 0;			// 생성데이터 갯수. 필수.
	private int gridSize 		= 0;			// 병렬처리할 쓰레드 갯수. 옵션(기본값 1).
	/*********************************** 멤버변수 선언 끝 ***********************************/ 
	
	/**
	 * Job 구성
	 */
	@Override
	public void configJob() throws Exception {
		callLog(this, "configJob");
		
        /*******************************************************************
        1. 테이블 SAMPLE_TEST 에 테스트데이터를 입력
        	실행파라메터 : spring.batch.job.names=tableDataGenType01Job dataCnt=10000
        *******************************************************************/

		/*******************************************************************
		Job Parameter 를 JobConfig에서 사용하려면 configJob() 메소드에서 
		getInitJobParam(Key)로 얻어와서 아래와 같이 사용할 수 있음.
		*******************************************************************/
		dataCnt = Integer.parseInt( this.getInitJobParam("dataCnt", "10000") );
		gridSize = Integer.parseInt( this.getInitJobParam("gridSize", "1") );
		
		// 01. 기존데이터 삭제
		this.addTasklet(new TableDeleteTasklet(this.sqlBatchSessionSample));
		// 02. 신규데이터 입력
		this.addTasklet(new TableInsertTasklet(this.sqlBatchSessionSample));
	}
	
}
