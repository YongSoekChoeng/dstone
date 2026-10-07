package sample.member;

import java.util.HashMap;
import java.util.Map;

public class MemoryMemberRepository implements MemberRepository {

    private final Map<String, Member> store = new HashMap<String, Member>();

    public void save(Member member) {
        store.put(member.getId(), member);
    }

}
