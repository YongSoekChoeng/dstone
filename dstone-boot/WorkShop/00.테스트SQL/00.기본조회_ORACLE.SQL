
/***********************************************************************************************************
1. 테이블 조회
***********************************************************************************************************/

SELECT 
	B.TABLE_NAME
	, B.COMMENTS TABLE_COMMENT
FROM 
	ALL_TABLES A
	, ALL_TAB_COMMENTS B
WHERE 1=1
	AND A.OWNER       = B.OWNER
	AND A.TABLE_NAME  = B.TABLE_NAME
    
    AND B.OWNER       = 'DBO'
    
    /* 테이블ID/테이블명 */
	AND A.TABLE_NAME LIKE '%' || 'TB_PP_PP_M'
	AND B.COMMENTS LIKE '%' || '' || '%'
    

/***********************************************************************************************************
2. 테이블컬럼 조회
***********************************************************************************************************/

SELECT 
	C.OWNER
	, B.TABLE_NAME
	, B.COMMENTS TABLE_COMMENT
	, C.COLUMN_NAME
	, C.DATA_TYPE ||
	CASE WHEN C.DATA_TYPE LIKE '%CHAR%'THEN
			'(' || C.DATA_LENGTH || ')'
		WHEN C.DATA_TYPE = 'NUMBER' AND C.DATA_PRECISION > 0 AND C.DATA_SCALE > 0 THEN
			'(' || C.DATA_PRECISION || ',' || C.DATA_SCALE || ')'
		WHEN C.DATA_TYPE = 'NUMBER' AND C.DATA_PRECISION > 0 THEN
			'(' || C.DATA_PRECISION || ')'
	END AS DATA_TYPE
	, D.COMMENTS COLUMN_COMMENT
    , P.PK
    , ', A.'||C.COLUMN_NAME||' /* '|| D.COMMENTS  ||' */' AS QEURY
FROM 
	ALL_TAB_COMMENTS B
	, ALL_TAB_COLUMNS  C
	, ALL_COL_COMMENTS D
    , (
        SELECT
            COLS.OWNER
            , COLS.TABLE_NAME
            , COLS.COLUMN_NAME
            , 'Y' PK
        FROM
            ALL_CONS_COLUMNS COLS 
            , ALL_CONSTRAINTS CONS
        WHERE 1=1
            AND CONS.OWNER = COLS.OWNER
            AND CONS.TABLE_NAME = COLS.TABLE_NAME
            AND CONS.CONSTRAINT_NAME = COLS.CONSTRAINT_NAME
            AND CONS.CONSTRAINT_TYPE = 'P'
    ) P
WHERE 1=1
	AND B.OWNER       = C.OWNER
	AND B.TABLE_NAME  = C.TABLE_NAME
	AND C.OWNER       = D.OWNER
	AND C.TABLE_NAME  = D.TABLE_NAME
	AND C.COLUMN_NAME = D.COLUMN_NAME
    AND B.OWNER       = P.OWNER(+)
    AND B.TABLE_NAME  = P.TABLE_NAME(+)
    AND C.COLUMN_NAME = P.COLUMN_NAME(+)
    
    AND B.OWNER       = 'DBO'
    
    /* 테이블ID/테이블명 */
	AND B.TABLE_NAME LIKE '%' || 'TB_PP_PP_M'
	AND B.COMMENTS LIKE '%' || '' || '%'
    
    /* 컬럼ID/컬럼명 */
	AND D.COLUMN_NAME LIKE '%' || ''
	AND D.COMMENTS LIKE '%' || '' || '%'
    

/***********************************************************************************************************
3. 기본 조회
***********************************************************************************************************/

SELECT 
	A.*
FROM 
	TB_PP_PP_M A
WHERE 1=1
    /* 조회조건 */
	AND B.COL1 LIKE '%' || ''
    

