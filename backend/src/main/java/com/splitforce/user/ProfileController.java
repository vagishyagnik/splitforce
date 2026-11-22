package com.splitforce.user;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.splitforce.group.GroupMember;
import com.splitforce.group.GroupMemberRepository;
import com.splitforce.group.GroupRepository;

/**
 * A member's profile as seen by a group-mate: basic details plus the groups you
 * share. Readable only by someone who shares at least one group with the target,
 * so a profile can't be enumerated by userId and strangers reveal nothing.
 */
@RestController
public class ProfileController {

    private static final String PLACEHOLDER_SUFFIX = "@placeholder.splitforce.local";

    private final UserService userService;
    private final UserRepository users;
    private final GroupMemberRepository groupMembers;
    private final GroupRepository groups;

    public ProfileController(UserService userService,
                             UserRepository users,
                             GroupMemberRepository groupMembers,
                             GroupRepository groups) {
        this.userService = userService;
        this.users = users;
        this.groupMembers = groupMembers;
        this.groups = groups;
    }

    @GetMapping("/api/users/{userId}")
    public ResponseEntity<Map<String, Object>> profile(@AuthenticationPrincipal OAuth2User principal,
                                                       @PathVariable("userId") String userId) {
        if (principal == null) return ResponseEntity.status(401).build();
        User me = userService.resolveOrCreate(principal);
        boolean isSelf = me.getUserId().equals(userId);

        User target = users.findById(userId).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();

        Set<String> myGroupIds = new HashSet<>();
        for (GroupMember gm : groupMembers.listByUser(me.getUserId())) myGroupIds.add(gm.getGroupId());

        List<Map<String, Object>> common = new ArrayList<>();
        for (GroupMember gm : groupMembers.listByUser(userId)) {
            if (!myGroupIds.contains(gm.getGroupId())) continue;
            groups.findById(gm.getGroupId()).ifPresent(g -> {
                Map<String, Object> grp = new LinkedHashMap<>();
                grp.put("groupId", g.getGroupId());
                grp.put("name", g.getName());
                grp.put("type", g.getType());
                grp.put("currency", g.getBaseCurrency());
                common.add(grp);
            });
        }

        // A profile only exists for you if you share a group — otherwise don't reveal it.
        if (!isSelf && common.isEmpty()) return ResponseEntity.notFound().build();

        common.sort(Comparator.comparing(g -> String.valueOf(g.get("name")).toLowerCase()));

        String email = target.getEmail();
        if (email != null && email.endsWith(PLACEHOLDER_SUFFIX)) email = null;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", target.getUserId());
        body.put("name", target.getName());
        body.put("email", email);
        body.put("upiId", userService.plainUpiId(target));
        body.put("picture", userService.resolvePictureUrl(target));
        body.put("paymentQr", userService.resolvePaymentQrUrl(target));
        body.put("placeholder", target.isPlaceholder());
        body.put("isYou", isSelf);
        body.put("commonGroups", common);
        return ResponseEntity.ok(body);
    }
}
