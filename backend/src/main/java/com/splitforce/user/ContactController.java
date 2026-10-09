package com.splitforce.user;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.splitforce.group.GroupMember;
import com.splitforce.group.GroupMemberRepository;

@RestController
public class ContactController {

    private static final String PLACEHOLDER_SUFFIX = "@placeholder.splitforce.local";

    private final UserService userService;
    private final UserRepository users;
    private final GroupMemberRepository groupMembers;

    public ContactController(UserService userService,
                             UserRepository users,
                             GroupMemberRepository groupMembers) {
        this.userService = userService;
        this.users = users;
        this.groupMembers = groupMembers;
    }

    @GetMapping("/api/me/contacts")
    public List<Map<String, Object>> myContacts(@AuthenticationPrincipal OAuth2User principal) {
        User me = userService.resolveOrCreate(principal);
        Set<String> otherIds = new HashSet<>();
        for (GroupMember mine : groupMembers.listByUser(me.getUserId())) {
            for (GroupMember peer : groupMembers.listByGroup(mine.getGroupId())) {
                if (!me.getUserId().equals(peer.getUserId())) {
                    otherIds.add(peer.getUserId());
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        for (String uid : otherIds) {
            users.findById(uid).ifPresent(u -> {
                String email = u.getEmail();
                if (email == null || email.endsWith(PLACEHOLDER_SUFFIX)) return;
                if (!seenEmails.add(email.toLowerCase())) return;
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("userId", u.getUserId());
                dto.put("email", email);
                dto.put("name", u.getName());
                dto.put("picture", UserService.resolvePictureUrl(u));
                out.add(dto);
            });
        }
        out.sort(Comparator.comparing(
                m -> ((String) (m.get("name") != null ? m.get("name") : m.get("email"))).toLowerCase()));
        return out;
    }
}
