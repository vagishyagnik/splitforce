package com.splitforce.activity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.splitforce.expense.Expense;
import com.splitforce.expense.ExpenseRepository;
import com.splitforce.group.Group;
import com.splitforce.group.GroupMember;
import com.splitforce.group.GroupMemberRepository;
import com.splitforce.group.GroupRepository;
import com.splitforce.user.User;
import com.splitforce.user.UserService;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final UserService userService;
    private final GroupRepository groups;
    private final GroupMemberRepository groupMembers;
    private final ExpenseRepository expenses;

    public ActivityController(UserService userService,
                              GroupRepository groups,
                              GroupMemberRepository groupMembers,
                              ExpenseRepository expenses) {
        this.userService = userService;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.expenses = expenses;
    }

    @GetMapping
    public List<Map<String, Object>> myActivities(@AuthenticationPrincipal OAuth2User principal) {
        User me = userService.resolveOrCreate(principal);
        List<GroupMember> memberships = groupMembers.listByUser(me.getUserId());

        Map<String, Group> groupsById = new HashMap<>();
        for (GroupMember m : memberships) {
            groups.findById(m.getGroupId()).ifPresent(g -> groupsById.put(g.getGroupId(), g));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Group g : groupsById.values()) {
            List<Expense> groupExpenses = expenses.listByGroup(g.getGroupId());
            int memberCount = groupMembers.listByGroup(g.getGroupId()).size();

            BigDecimal myNet = BigDecimal.ZERO;
            String lastActivityAt = null;
            for (Expense e : groupExpenses) {
                BigDecimal share = e.getShares() == null ? null : e.getShares().get(me.getUserId());
                if (share != null) myNet = myNet.add(share.multiply(rateToBase(g, e.getCurrency())));
                if (e.getDate() != null && (lastActivityAt == null || e.getDate().compareTo(lastActivityAt) > 0)) {
                    lastActivityAt = e.getDate();
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("groupId", g.getGroupId());
            row.put("name", g.getName());
            row.put("type", g.getType());
            row.put("currency", g.getBaseCurrency());
            row.put("memberCount", memberCount);
            row.put("expenseCount", groupExpenses.size());
            row.put("lastActivityAt", lastActivityAt);
            row.put("myNet", myNet.setScale(2, RoundingMode.HALF_UP));
            out.add(row);
        }

        out.sort(Comparator.comparing(
                (Map<String, Object> r) -> (String) r.get("lastActivityAt"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    /** Same conversion as the group page: a missing rate counts 1:1. */
    private static BigDecimal rateToBase(Group g, String currency) {
        if (currency == null || currency.isBlank() || currency.equalsIgnoreCase(g.getBaseCurrency())) return BigDecimal.ONE;
        BigDecimal r = g.getFxRates() == null ? null : g.getFxRates().get(currency.toUpperCase());
        return r == null ? BigDecimal.ONE : r;
    }
}
