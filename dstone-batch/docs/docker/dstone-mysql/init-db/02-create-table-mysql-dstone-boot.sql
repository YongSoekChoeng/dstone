/**********************************************
Sample 데이터베이스[Application용] 테이블
**********************************************/

USE sampleDB;

CREATE TABLE IF NOT EXISTS SAMPLE_TEST (
  TEST_ID VARCHAR(30) NOT NULL, 
  TEST_NAME VARCHAR(200), 
  FLAG_YN VARCHAR(1), 
  INPUT_DT DATE NOT NULL,  
  PRIMARY KEY  (TEST_ID)
) ;
