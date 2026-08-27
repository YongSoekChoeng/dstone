package net.dstone.boot.test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import net.dstone.common.utils.DateUtil;
import net.dstone.common.utils.StringUtil;

public class TestBean {

	public static void main(String[] args) {
		
		TestBean.KAFKA테스트();
		
		//TestBean.테스트();
		//TestBean.암복호화();
		//TestBean.파일분리();
		//TestBean.DB테스트();
		//TestBean.레빗엠큐테스트();
		//TestBean.레디스테스트();
		//TestBean.KAFKA테스트();
	}
	

	public static void 테스트() {
		try {
			
			String[] fileList = net.dstone.common.utils.FileUtil.readFileListAll("D:\\AppHome\\framework");
			for( String file : fileList ) {
				if( file.endsWith(".md") ) {

//					String filePath = net.dstone.common.utils.FileUtil.getFilePath(file);
//					String fileName = net.dstone.common.utils.FileUtil.getFileName(file, true);
//					String fileConts = net.dstone.common.utils.FileUtil.readFile(file);

					System.out.println(file);
					
//					String str = "docker-compose -";
//					if(fileConts.indexOf(str) > -1) {
//						String repl = "docker compose -";
//						fileConts = net.dstone.common.utils.StringUtil.replace(fileConts, str, repl);
//						net.dstone.common.utils.FileUtil.writeFile(filePath, fileName, fileConts);
//					}
					
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	public static void 암복호화() {

		/*****************************************************/
    
		/*****************************************************/

		net.dstone.common.utils.DateUtil.stopWatchStart("01.암복호화");

		try {

			String plainStr = "";
			String encStr = "NY+sJtk3ncS4BARnhaHgJKF/oAbqpjdILYPvFhyShAc=";
			String decStr = "";

			if (!net.dstone.common.utils.StringUtil.isEmpty(plainStr)) {
				encStr = net.dstone.common.utils.EncUtil.encrypt(plainStr);
				System.out.println("plainStr[" + plainStr + "] ==>> encStr[" + encStr + "]");
			}

			if (!net.dstone.common.utils.StringUtil.isEmpty(encStr)) {
				decStr = net.dstone.common.utils.EncUtil.decrypt(encStr);
				System.out.println("encStr[" + encStr + "] ==>> decStr[" + decStr + "]");
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			net.dstone.common.utils.DateUtil.stopWatchEnd("01.암복호화");
		}

	}
	
	public static void 파일분리() {

		/*****************************************************/
    
		/*****************************************************/

		net.dstone.common.utils.DateUtil.stopWatchStart("01.파일분리");

		try {

			String inputFileFullPath = "C:/Temp/SAMFILE_TEST/SAMFILE01.sam";
			String outputDir = "C:/Temp/SAMFILE_TEST/split";
			int fileSizeByMB = 1;

			java.util.List<String> splitTextFileList = net.dstone.common.utils.FileUtil.splitTextFile(inputFileFullPath, outputDir, fileSizeByMB);

			for(String splitTextFile : splitTextFileList) {
				System.out.println("splitTextFile[" + splitTextFile + "]");
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			net.dstone.common.utils.DateUtil.stopWatchEnd("01.파일분리");
		}

	}

	public static void DB테스트() {
		String DBID = "DBID_0";
		net.dstone.common.utils.DbUtil db = null;
		net.dstone.common.utils.DataSet ds = new net.dstone.common.utils.DataSet();
		StringBuffer sql = new StringBuffer();
		int genNum = 100;
		net.dstone.common.utils.DateUtil.stopWatchStart("연습장");
		try {
			sql.append("INSERT INTO SAMPLE_TEST (TEST_ID, TEST_NAME, FLAG_YN, INPUT_DT) VALUES (?, ?, 'N', NOW())");
			
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection(false);
			db.setQuery(sql.toString());
			
			for(int i=0; i<genNum; i++){
				db.pstmt.setString(1, StringUtil.filler(String.valueOf((i+1)), 10, "0"));
				db.pstmt.setString(2, String.valueOf((i+1))+"-이름");
				db.pstmt.addBatch();
				//db.pstmt.clearParameters();
				if( i%5000 == 0){
					db.pstmt.executeBatch();
				}
			}
		
			db.pstmt.executeBatch();
			
			db.commit();
			
		}catch(Exception e){
			e.printStackTrace();
			System.out.println(db.getQuery());

			db.rollBack();
		}finally{
			if(db != null){
				db.release();
			}
			net.dstone.common.utils.DateUtil.stopWatchEnd("연습장");
		}
	}
	
	public static void 레빗엠큐테스트() {
		
		/*****************************************************/
		
		String url = "http://localhost:7081/dstone-mq/rabbitmq/producer/sendMessage.do";
		String method = "POST";
		
		net.dstone.common.utils.DataSet paramDs = new net.dstone.common.utils.DataSet();
		/*
		paramDs.setDatum("exchangeId", "app.fanout.exchange");
		paramDs.setDatum("routingKey", "");
		paramDs.setDatum("message", "fanout 메세지... 헐헐헐~");
		*/
		
		paramDs.setDatum("exchangeId", "app.direct.exchange");
		paramDs.setDatum("routingKey", "orders.process");
		paramDs.setDatum("message", "direct 메세지... 헐헐헐~ 보낸시간:" + DateUtil.getToDate("yyyyMMdd-HH:mm:ss"));
		
		/*****************************************************/
		
		net.dstone.common.utils.DateUtil.stopWatchStart("01.레빗엠큐테스트");
		
		net.dstone.common.utils.WcUtil ws = new net.dstone.common.utils.WcUtil();
		try {
			net.dstone.common.utils.WcUtil.Bean wsBean = new net.dstone.common.utils.WcUtil.Bean();
			
			String[] paramKeys = paramDs.getChildrenKeys();
			if(paramKeys != null){
				for(String key : paramKeys){
					wsBean.addParam(key, paramDs.getDatum(key));
				}
			}
			
			wsBean.url = url;
			wsBean.method = method;		
			wsBean.setContentType(net.dstone.common.utils.WcUtil.CONT_TYPE_FORM);
		
			ws.execute(wsBean);
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
			//System.out.println( ws.response.outputStr );
			net.dstone.common.utils.DateUtil.stopWatchEnd("01.레빗엠큐테스트");
		}
		

	}
	

	public static void 레디스테스트() {
		
		/*****************************************************/
		java.util.Map<String,Object> initValMap = new java.util.HashMap<String,Object>();
		initValMap.put("spring.data.redis.host", "localhost");
		initValMap.put("spring.data.redis.port", "6379");
		initValMap.put("spring.data.redis.password", "db2admin!@");
		
		java.util.Map<String,Object> valMap = new java.util.HashMap<String,Object>();
		valMap.put("NAME", "정용석");
		valMap.put("AGE", "55");
		/*****************************************************/
		
		net.dstone.common.utils.DateUtil.stopWatchStart("01.레디스테스트");
		
		try {
			org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate = net.dstone.common.utils.RedisUtil.getInstance(initValMap).getRedisTemplate();
			
			org.springframework.data.redis.core.HashOperations<String, String, Object> setValueOperations = redisTemplate.opsForHash();
			setValueOperations.put("jysn007", "NAME", "정용석");
		
			org.springframework.data.redis.core.HashOperations<String, String, Object> getValueOperations = redisTemplate.opsForHash();
			System.out.println( "NAME:" +  getValueOperations.get("jysn007", "NAME") );
		    
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
			net.dstone.common.utils.DateUtil.stopWatchEnd("01.레디스테스트");
		}

	}


	public static void KAFKA테스트() {
		
		/*****************************************************/
		KafkaTemplate<String, Object> kafkaTemplate = null;
		Map<String,Object> event = new HashMap<String,Object>();
		event.put("orderId", "1");
		event.put("orderName", "테스트주문");
		event.put("orderItem", "연필");
		event.put("orderCount", "1000");
		/*****************************************************/
		
		net.dstone.common.utils.DateUtil.stopWatchStart("01.KAFKA테스트");
		
		try {


	        // 1. 카프카 프로듀서 설정 정보 (Pure Java Map)
	        Map<String, Object> configProps = new HashMap<>();
	        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
	        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
	        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
	        // 옵션 세팅 (선택)
	        configProps.put(ProducerConfig.ACKS_CONFIG, "all");

	        // 2. KafkaTemplate 직접 인스턴스화
	        kafkaTemplate = net.dstone.common.utils.KafkaUtil.getInstance(configProps).getKafkaTemplate();
			
	        // 3. KAFKA 전송
	        org.springframework.kafka.support.SendResult<String, Object> result = kafkaTemplate.send("order-events", event.get("orderId").toString(), event).get(5, TimeUnit.SECONDS);
		    System.out.println("전송 완료: " + result.getRecordMetadata());

		} catch (Exception e) {
			e.printStackTrace();
		} finally{
			if (kafkaTemplate != null) {
				kafkaTemplate.flush();
				kafkaTemplate.destroy(); // 내부 producerFactory까지 종료
			}
			net.dstone.common.utils.DateUtil.stopWatchEnd("01.KAFKA테스트");
		}

	}
	
	
}
