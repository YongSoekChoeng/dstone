package sample.member;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Lombok 으로 getter/setter/빌더를 만드는 클래스. 소스에는 getName() 도 builder() 도 없다.
 */
@Getter
@Setter
@Builder
public class Member {

    private final String id;
    private String name;
    private boolean isActive;

}
