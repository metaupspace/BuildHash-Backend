-- H4.7: Scope GST sequence allocation to (sequence_type, fiscal_year) composite primary key

ALTER TABLE gst_sequences DROP CONSTRAINT gst_sequences_pkey;
ALTER TABLE gst_sequences ADD PRIMARY KEY (sequence_type, fiscal_year);
