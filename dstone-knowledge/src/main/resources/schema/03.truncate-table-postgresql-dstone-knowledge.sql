TRUNCATE 
	rag_embedding
	, rag_chunk
	, rag_document
	, analysis_resource
	, analysis_config
	, analysis_relation
	, analysis_reference
	, analysis_mapper
	, analysis_endpoint
	, analysis_annotation
	, analysis_field
	, analysis_method
	, analysis_symbol
	, analysis_file_pass
	, analysis_file
	, analysis_error
	, analysis_metric
	, analysis_revision_pass
	, analysis_job;

DELETE FROM analysis_revision;
DELETE FROM analysis_project;
commit;