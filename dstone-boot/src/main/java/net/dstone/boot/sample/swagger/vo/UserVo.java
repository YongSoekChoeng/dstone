package net.dstone.boot.sample.swagger.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 정보를 담는 모델")
public class UserVo {
	@Schema(description = "사용자 고유 ID", example = "user_001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;
	@Schema(description = "사용자 이름", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	@Override
	public String toString() {
		return "User [id=" + id + ", name=" + name + "]";
	}
}
