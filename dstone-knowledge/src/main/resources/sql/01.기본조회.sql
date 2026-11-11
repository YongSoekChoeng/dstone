

select 
	--distinct resource_type 
	*
from 
	/************************************************************************************
	<시작>
	analysis_project    
	analysis_revision
	analysis_job        
	analysis_revision_pass
	-------------------------------------------------------------------------------------
	<SCAN/CARRY 단계>        <DECLARE 단계>                  <RESOURCE 단계>
	analysis_file           analysis_symbol                analysis_config        
	analysis_file_pass      analysis_field                 analysis_mapper(MYBATIS/IBATIS)
	                        analysis_method                analysis_resource
	                        analysis_annotation    
	                        analysis_mapper(Java 문자열 SQL)
	                        analysis_reference
	-------------------------------------------------------------------------------------				       
	<RESOLVE 단계>           <LINK 단계>                    <SEMANTIC 단계>
	analysis_relation       analysis_relation(추가입력)     analysis_endpoint
	analysis_reference(수정) analysis_metric               analysis_relation(추가입력)
	analysis_error                                        analysis_symbol(수정)
	                                                      analysis_mapper(추가입력,수정)
	                                                      analysis_method(수정)
	                                                      analysis_resource(추가입력)	
	************************************************************************************/
	public.analysis_relation 
where 1=1
	and revision_id = 116
	--and from_id = 'e877bffc99c843d5fc8e712ebeb6cd813c8644fd'
	
	/* *****************************************************
	and from_id = 'e877bffc99c843d5fc8e712ebeb6cd813c8644fd'
	
	and symbol_id = '49e766a6aca09ce427d1f1b4cb2ede29a781dea7'
	and field_id = '7f6e28c8de1fbe16c75c0f6788777da2dfc0e71f'
	and method_id = 'e877bffc99c843d5fc8e712ebeb6cd813c8644fd'
	***************************************************** */	
--order by updated_at asc



