package cn.nkpro.tfms.platform.elasticearch;

import lombok.Data;

@Data
public class ESBucket {
    private String key;
	private Long docCount;
	private Number sum;
	private Number max;
	private Number min;
	private Number avg;
}