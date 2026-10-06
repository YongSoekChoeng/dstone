package net.dstone.batch.common.consts;

public class Constants {
	/*** Partition 관련 Key 변수명 ***/
	public class Partition{
		/*** DB Partition 관련 Key 값 ***/
		public final static String PARTITION_YN 		= "PARTITION_YN";
		public final static String GRID_SIZE 			= "GRID_SIZE";
		public final static String CHUNK_SIZE 			= "CHUNK_SIZE";
		public final static String KEY_COLUMN 			= "KEY_COLUMN";

		/*** File Partition 관련 Key 값 ***/
		public final static String INPUT_FILE_PATH 		= "inputFileFullPath";
		public final static String OUTPUT_FILE_PATH 	= "outputFileFullPath";
		public final static String FROM_LINE 			= "fromLine";
		public final static String TO_LINE 				= "toLine";
	}
	
}
