package net.dstone.boot.sample.kakao.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashMap;

@NoArgsConstructor //역직렬화를 위한 기본 생성자
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoUserInfoResponseVo {

    //회원 번호
    @JsonProperty("id")
    public Long id;

    //자동 연결 설정을 비활성화한 경우만 존재.
    //true : 연결 상태, false : 연결 대기 상태
    @JsonProperty("has_signed_up")
    public Boolean hasSignedUp;

    //서비스에 연결 완료된 시각. UTC
    @JsonProperty("connected_at")
    public Date connectedAt;

    //카카오싱크 간편가입을 통해 로그인한 시각. UTC
    @JsonProperty("synched_at")
    public Date synchedAt;

    //사용자 프로퍼티
    @JsonProperty("properties")
    public HashMap<String, String> properties;

    //카카오 계정 정보
    @JsonProperty("kakao_account")
    public KakaoAccount kakaoAccount;

    //uuid 등 추가 정보
    @JsonProperty("for_partner")
    public Partner partner;

    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class KakaoAccount {

        //프로필 정보 제공 동의 여부
        @JsonProperty("profile_needs_agreement")
        public Boolean isProfileAgree;

        //닉네임 제공 동의 여부
        @JsonProperty("profile_nickname_needs_agreement")
        public Boolean isNickNameAgree;

        //프로필 사진 제공 동의 여부
        @JsonProperty("profile_image_needs_agreement")
        public Boolean isProfileImageAgree;

        //사용자 프로필 정보
        @JsonProperty("profile")
        public Profile profile;

        //이름 제공 동의 여부
        @JsonProperty("name_needs_agreement")
        public Boolean isNameAgree;

        //카카오계정 이름
        @JsonProperty("name")
        public String name;

        //이메일 제공 동의 여부
        @JsonProperty("email_needs_agreement")
        public Boolean isEmailAgree;

        //이메일이 유효 여부
        // true : 유효한 이메일, false : 이메일이 다른 카카오 계정에 사용돼 만료
        @JsonProperty("is_email_valid")
        public Boolean isEmailValid;

        //이메일이 인증 여부
        //true : 인증된 이메일, false : 인증되지 않은 이메일
        @JsonProperty("is_email_verified")
        public Boolean isEmailVerified;

        //카카오계정 대표 이메일
        @JsonProperty("email")
        public String email;

        //연령대 제공 동의 여부
        @JsonProperty("age_range_needs_agreement")
        public Boolean isAgeAgree;

        //연령대
        //참고 https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api#req-user-info
        @JsonProperty("age_range")
        public String ageRange;

        //출생 연도 제공 동의 여부
        @JsonProperty("birthyear_needs_agreement")
        public Boolean isBirthYearAgree;

        //출생 연도 (YYYY 형식)
        @JsonProperty("birthyear")
        public String birthYear;

        //생일 제공 동의 여부
        @JsonProperty("birthday_needs_agreement")
        public Boolean isBirthDayAgree;

        //생일 (MMDD 형식)
        @JsonProperty("birthday")
        public String birthDay;

        //생일 타입
        // SOLAR(양력) 혹은 LUNAR(음력)
        @JsonProperty("birthday_type")
        public String birthDayType;

        //성별 제공 동의 여부
        @JsonProperty("gender_needs_agreement")
        public Boolean isGenderAgree;

        //성별
        @JsonProperty("gender")
        public String gender;

        //전화번호 제공 동의 여부
        @JsonProperty("phone_number_needs_agreement")
        public Boolean isPhoneNumberAgree;

        //전화번호
        //국내 번호인 경우 +82 00-0000-0000 형식
        @JsonProperty("phone_number")
        public String phoneNumber;

        //CI 동의 여부
        @JsonProperty("ci_needs_agreement")
        public Boolean isCIAgree;

        //CI, 연계 정보
        @JsonProperty("ci")
        public String ci;

        //CI 발급 시각, UTC
        @JsonProperty("ci_authenticated_at")
        public Date ciCreatedAt;

        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public class Profile {

            //닉네임
            @JsonProperty("nickname")
            public String nickName;

            //프로필 미리보기 이미지 URL
            @JsonProperty("thumbnail_image_url")
            public String thumbnailImageUrl;

            //프로필 사진 URL
            @JsonProperty("profile_image_url")
            public String profileImageUrl;

            //프로필 사진 URL 기본 프로필인지 여부
            //true : 기본 프로필, false : 사용자 등록
            @JsonProperty("is_default_image")
            public String isDefaultImage;

            //닉네임이 기본 닉네임인지 여부
            //true : 기본 닉네임, false : 사용자 등록
            @JsonProperty("is_default_nickname")
            public Boolean isDefaultNickName;

			public String getNickName() {
				return nickName;
			}

			public void setNickName(String nickName) {
				this.nickName = nickName;
			}

			public String getThumbnailImageUrl() {
				return thumbnailImageUrl;
			}

			public void setThumbnailImageUrl(String thumbnailImageUrl) {
				this.thumbnailImageUrl = thumbnailImageUrl;
			}

			public String getProfileImageUrl() {
				return profileImageUrl;
			}

			public void setProfileImageUrl(String profileImageUrl) {
				this.profileImageUrl = profileImageUrl;
			}

			public String getIsDefaultImage() {
				return isDefaultImage;
			}

			public void setIsDefaultImage(String isDefaultImage) {
				this.isDefaultImage = isDefaultImage;
			}

			public Boolean getIsDefaultNickName() {
				return isDefaultNickName;
			}

			public void setIsDefaultNickName(Boolean isDefaultNickName) {
				this.isDefaultNickName = isDefaultNickName;
			}

        }

		public Boolean getIsProfileAgree() {
			return isProfileAgree;
		}

		public void setIsProfileAgree(Boolean isProfileAgree) {
			this.isProfileAgree = isProfileAgree;
		}

		public Boolean getIsNickNameAgree() {
			return isNickNameAgree;
		}

		public void setIsNickNameAgree(Boolean isNickNameAgree) {
			this.isNickNameAgree = isNickNameAgree;
		}

		public Boolean getIsProfileImageAgree() {
			return isProfileImageAgree;
		}

		public void setIsProfileImageAgree(Boolean isProfileImageAgree) {
			this.isProfileImageAgree = isProfileImageAgree;
		}

		public Profile getProfile() {
			return profile;
		}

		public void setProfile(Profile profile) {
			this.profile = profile;
		}

		public Boolean getIsNameAgree() {
			return isNameAgree;
		}

		public void setIsNameAgree(Boolean isNameAgree) {
			this.isNameAgree = isNameAgree;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Boolean getIsEmailAgree() {
			return isEmailAgree;
		}

		public void setIsEmailAgree(Boolean isEmailAgree) {
			this.isEmailAgree = isEmailAgree;
		}

		public Boolean getIsEmailValid() {
			return isEmailValid;
		}

		public void setIsEmailValid(Boolean isEmailValid) {
			this.isEmailValid = isEmailValid;
		}

		public Boolean getIsEmailVerified() {
			return isEmailVerified;
		}

		public void setIsEmailVerified(Boolean isEmailVerified) {
			this.isEmailVerified = isEmailVerified;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

		public Boolean getIsAgeAgree() {
			return isAgeAgree;
		}

		public void setIsAgeAgree(Boolean isAgeAgree) {
			this.isAgeAgree = isAgeAgree;
		}

		public String getAgeRange() {
			return ageRange;
		}

		public void setAgeRange(String ageRange) {
			this.ageRange = ageRange;
		}

		public Boolean getIsBirthYearAgree() {
			return isBirthYearAgree;
		}

		public void setIsBirthYearAgree(Boolean isBirthYearAgree) {
			this.isBirthYearAgree = isBirthYearAgree;
		}

		public String getBirthYear() {
			return birthYear;
		}

		public void setBirthYear(String birthYear) {
			this.birthYear = birthYear;
		}

		public Boolean getIsBirthDayAgree() {
			return isBirthDayAgree;
		}

		public void setIsBirthDayAgree(Boolean isBirthDayAgree) {
			this.isBirthDayAgree = isBirthDayAgree;
		}

		public String getBirthDay() {
			return birthDay;
		}

		public void setBirthDay(String birthDay) {
			this.birthDay = birthDay;
		}

		public String getBirthDayType() {
			return birthDayType;
		}

		public void setBirthDayType(String birthDayType) {
			this.birthDayType = birthDayType;
		}

		public Boolean getIsGenderAgree() {
			return isGenderAgree;
		}

		public void setIsGenderAgree(Boolean isGenderAgree) {
			this.isGenderAgree = isGenderAgree;
		}

		public String getGender() {
			return gender;
		}

		public void setGender(String gender) {
			this.gender = gender;
		}

		public Boolean getIsPhoneNumberAgree() {
			return isPhoneNumberAgree;
		}

		public void setIsPhoneNumberAgree(Boolean isPhoneNumberAgree) {
			this.isPhoneNumberAgree = isPhoneNumberAgree;
		}

		public String getPhoneNumber() {
			return phoneNumber;
		}

		public void setPhoneNumber(String phoneNumber) {
			this.phoneNumber = phoneNumber;
		}

		public Boolean getIsCIAgree() {
			return isCIAgree;
		}

		public void setIsCIAgree(Boolean isCIAgree) {
			this.isCIAgree = isCIAgree;
		}

		public String getCi() {
			return ci;
		}

		public void setCi(String ci) {
			this.ci = ci;
		}

		public Date getCiCreatedAt() {
			return ciCreatedAt;
		}

		public void setCiCreatedAt(Date ciCreatedAt) {
			this.ciCreatedAt = ciCreatedAt;
		}
    }

    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Partner {
        //고유 ID
        @JsonProperty("uuid")
        public String uuid;

		public String getUuid() {
			return uuid;
		}

		public void setUuid(String uuid) {
			this.uuid = uuid;
		}
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Boolean getHasSignedUp() {
		return hasSignedUp;
	}

	public void setHasSignedUp(Boolean hasSignedUp) {
		this.hasSignedUp = hasSignedUp;
	}

	public Date getConnectedAt() {
		return connectedAt;
	}

	public void setConnectedAt(Date connectedAt) {
		this.connectedAt = connectedAt;
	}

	public Date getSynchedAt() {
		return synchedAt;
	}

	public void setSynchedAt(Date synchedAt) {
		this.synchedAt = synchedAt;
	}

	public HashMap<String, String> getProperties() {
		return properties;
	}

	public void setProperties(HashMap<String, String> properties) {
		this.properties = properties;
	}

	public KakaoAccount getKakaoAccount() {
		return kakaoAccount;
	}

	public void setKakaoAccount(KakaoAccount kakaoAccount) {
		this.kakaoAccount = kakaoAccount;
	}

	public Partner getPartner() {
		return partner;
	}

	public void setPartner(Partner partner) {
		this.partner = partner;
	}

}
