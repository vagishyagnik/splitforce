package com.splitforce.mcp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.splitforce.activity.ActivityController;
import com.splitforce.group.GroupController;
import com.splitforce.group.GroupController.AddMembersRequest;
import com.splitforce.group.GroupController.CreateExpenseRequest;
import com.splitforce.group.GroupController.CreateGroupRequest;
import com.splitforce.group.GroupController.FxRateRequest;
import com.splitforce.group.GroupController.MemberInput;
import com.splitforce.group.GroupController.Participant;
import com.splitforce.group.GroupController.SettleRequest;
import com.splitforce.group.GroupController.UpdateGroupRequest;
import com.splitforce.group.GroupController.UpdateNotesRequest;
import com.splitforce.user.ContactController;
import com.splitforce.user.UserController;

/**
 * The SplitForce tools exposed over MCP, ported from the stdio server (mcp/src/index.js). Each tool
 * reuses the existing REST controllers — no domain logic is reimplemented here — by calling their
 * public methods with the authenticated {@link OAuth2User} and consuming the Map/ResponseEntity they
 * return. Lookups let the model refer to groups and people by name rather than id.
 */
@Component
public class McpTools {

    private final GroupController groups;
    private final ActivityController activities;
    private final ContactController contacts;
    private final UserController users;

    private List<Map<String, Object>> cachedDefinitions;

    public McpTools(GroupController groups, ActivityController activities,
                    ContactController contacts, UserController users) {
        this.groups = groups;
        this.activities = activities;
        this.contacts = contacts;
        this.users = users;
    }

    // ---------- Dispatch ----------

    public Object call(OAuth2User principal, String name, JsonNode args) {
        if (principal == null) throw new McpToolException("not authenticated");
        JsonNode a = args == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : args;
        try {
            return switch (name) {
                case "whoami" -> body(users.me(principal));
                case "list_groups" -> activities.myActivities(principal);
                case "get_group" -> groupSummary(loadGroup(principal, reqStr(a, "group")));
                case "list_expenses" -> listExpenses(principal, a);
                case "list_contacts" -> contacts.myContacts(principal);
                case "export_group_csv" -> exportCsv(principal, a);
                case "add_expense" -> addExpense(principal, a);
                case "update_expense" -> updateExpense(principal, a);
                case "delete_expense" -> deleteExpense(principal, a);
                case "record_settlement" -> recordSettlement(principal, a);
                case "create_group" -> createGroup(principal, a);
                case "rename_group" -> renameGroup(principal, a);
                case "add_members" -> addMembers(principal, a);
                case "update_group_notes" -> updateNotes(principal, a);
                case "set_fx_rate" -> setFxRate(principal, a);
                case "remove_fx_rate" -> removeFxRate(principal, a);
                case "delete_group" -> deleteGroup(principal, a);
                default -> throw new McpToolException("unknown tool: " + name);
            };
        } catch (ResponseStatusException e) {
            // Domain rejections (not-found, forbidden, validation) become readable tool errors.
            String reason = e.getReason() != null ? e.getReason() : e.getMessage();
            throw new McpToolException(reason);
        } catch (IllegalArgumentException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    // ---------- Read tools ----------

    private Object listExpenses(OAuth2User principal, JsonNode a) {
        Map<String, Object> group = loadGroup(principal, reqStr(a, "group"));
        List<Map<String, Object>> expenses = expensesOf(group);

        String personId = a.hasNonNull("person") ? (String) resolveMember(group, a.get("person").asText(), false).get("userId") : null;
        String payerId = a.hasNonNull("paidBy") ? (String) resolveMember(group, a.get("paidBy").asText(), false).get("userId") : null;
        String category = optStr(a, "category");
        String from = optStr(a, "from");
        String to = optStr(a, "to");
        String q = a.hasNonNull("search") ? a.get("search").asText().toLowerCase() : null;
        boolean includeSettlements = !a.has("includeSettlements") || a.get("includeSettlements").asBoolean(true);
        int limit = a.hasNonNull("limit") ? a.get("limit").asInt(50) : 50;
        if (limit < 1) limit = 1;
        if (limit > 500) limit = 500;

        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> e : expenses) {
            if (!includeSettlements && isSettlement(e)) continue;
            Map<String, Object> shares = sharesOf(e);
            String paidBy = (String) e.get("paidByUserId");
            if (personId != null && !personId.equals(paidBy) && !shares.containsKey(personId)) continue;
            if (payerId != null && !payerId.equals(paidBy)) continue;
            if (category != null && !category.equalsIgnoreCase(str(e.get("category")))) continue;
            String date = str(e.get("date"));
            if (from != null && date.compareTo(from) < 0) continue;
            if (to != null && date.compareTo(to) > 0) continue;
            if (q != null) {
                boolean hit = containsCi(str(e.get("description")), q)
                        || containsCi(str(e.get("category")), q)
                        || containsCi(str(e.get("notes")), q);
                if (!hit) continue;
            }
            matched.add(e);
        }

        List<Map<String, Object>> views = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, matched.size()); i++) views.add(expenseView(group, matched.get(i)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("group", group.get("name"));
        out.put("baseCurrency", group.get("baseCurrency"));
        out.put("total", matched.size());
        out.put("returned", views.size());
        out.put("expenses", views);
        return out;
    }

    private Object exportCsv(OAuth2User principal, JsonNode a) {
        String groupId = resolveGroupId(principal, reqStr(a, "group"));
        return body(groups.exportCsv(principal, groupId));
    }

    // ---------- Expenses ----------

    private Object addExpense(OAuth2User principal, JsonNode a) {
        Map<String, Object> group = loadGroup(principal, reqStr(a, "group"));
        String groupId = (String) group.get("groupId");
        Map<String, Object> payer = a.hasNonNull("paidBy")
                ? resolveMember(group, a.get("paidBy").asText(), true)
                : me(group);

        CreateExpenseRequest req = new CreateExpenseRequest();
        req.description = reqStr(a, "description");
        req.cost = reqDecimal(a, "cost");
        req.currency = (optStr(a, "currency") != null ? a.get("currency").asText() : str(group.get("baseCurrency"))).toUpperCase();
        req.date = optStr(a, "date");
        req.category = optStr(a, "category");
        req.notes = optStr(a, "notes");
        req.paidByUserId = (String) payer.get("userId");
        applySplit(req, buildSplit(group, a.get("split"), null));

        Map<String, Object> created = body(groups.createExpense(principal, groupId, req));
        Map<String, Object> fresh = groups.getGroup(principal, groupId);
        Map<String, Object> e = findExpense(fresh, (String) created.get("expenseId"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", e != null ? expenseView(fresh, e) : created);
        return out;
    }

    private Object updateExpense(OAuth2User principal, JsonNode a) {
        Map<String, Object> group = loadGroup(principal, reqStr(a, "group"));
        String groupId = (String) group.get("groupId");
        String expenseId = reqStr(a, "expenseId");
        Map<String, Object> old = findExpense(group, expenseId);
        if (old == null) {
            throw new McpToolException("No expense " + expenseId + " in " + group.get("name")
                    + ". Use list_expenses to find the id.");
        }

        // People already on the expense stay valid even if they've left the group.
        Set<String> onExpense = new java.util.HashSet<>();
        onExpense.addAll(sharesOf(old).keySet());
        onExpense.addAll(splitValuesOf(old).keySet());
        if (old.get("paidByUserId") != null) onExpense.add((String) old.get("paidByUserId"));

        boolean costChanged = a.hasNonNull("cost")
                && num(reqDecimal(a, "cost")) != num(old.get("cost"));

        Split split;
        if (a.hasNonNull("split")) {
            split = buildSplit(group, a.get("split"), onExpense);
        } else {
            String oldMode = str(old.get("splitMode"));
            Map<String, Object> oldValues = splitValuesOf(old);
            if (!oldMode.isEmpty() && !oldValues.isEmpty()) {
                if (oldMode.equals("amount") && costChanged) {
                    throw new McpToolException("This expense is split by exact amounts, so a new cost needs a new split.");
                }
                List<Participant> participants = new ArrayList<>();
                for (Map.Entry<String, Object> entry : oldValues.entrySet()) {
                    Participant p = new Participant();
                    p.userId = entry.getKey();
                    p.value = oldMode.equals("equal") ? null : toDecimal(entry.getValue());
                    participants.add(p);
                }
                split = new Split(oldMode, participants);
            } else {
                // Older expenses only have net shares; rebuild the split as the exact amounts owed.
                if (costChanged) {
                    throw new McpToolException("This expense predates saved splits, so a new cost needs a new split.");
                }
                Map<String, Object> shares = sharesOf(old);
                BigDecimal cost = toDecimal(old.get("cost"));
                String paidBy = (String) old.get("paidByUserId");
                List<Participant> participants = new ArrayList<>();
                for (String id : onExpense) {
                    double owed = (id.equals(paidBy) ? num(cost) : 0) - num(shares.get(id));
                    if (owed > 0.004) {
                        Participant p = new Participant();
                        p.userId = id;
                        p.value = round2(owed);
                        participants.add(p);
                    }
                }
                split = new Split("amount", participants);
            }
        }

        String paidByUserId = (String) old.get("paidByUserId");
        if (a.hasNonNull("paidBy")) {
            String ref = a.get("paidBy").asText();
            paidByUserId = onExpense.contains(ref) ? ref : (String) resolveMember(group, ref, false).get("userId");
        }

        CreateExpenseRequest req = new CreateExpenseRequest();
        req.description = a.hasNonNull("description") ? a.get("description").asText() : str(old.get("description"));
        req.cost = a.hasNonNull("cost") ? reqDecimal(a, "cost") : toDecimal(old.get("cost"));
        req.currency = (a.hasNonNull("currency") ? a.get("currency").asText() : str(old.get("currency"))).toUpperCase();
        req.date = a.hasNonNull("date") ? a.get("date").asText() : str(old.get("date"));
        // Empty string clears category/notes; a missing field keeps the old value.
        req.category = a.has("category") ? a.get("category").asText() : str(old.get("category"));
        req.notes = a.has("notes") ? a.get("notes").asText() : str(old.get("notes"));
        req.receiptKey = old.get("receiptKey") == null ? null : str(old.get("receiptKey"));
        req.paidByUserId = paidByUserId;
        applySplit(req, split);

        Map<String, Object> saved = groups.updateExpense(principal, groupId, expenseId, req);
        Map<String, Object> fresh = groups.getGroup(principal, groupId);
        Map<String, Object> e = findExpense(fresh, (String) saved.get("expenseId"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("updated", e != null ? expenseView(fresh, e) : saved);
        return out;
    }

    private Object deleteExpense(OAuth2User principal, JsonNode a) {
        String groupId = resolveGroupId(principal, reqStr(a, "group"));
        String expenseId = reqStr(a, "expenseId");
        groups.deleteExpense(principal, groupId, expenseId);
        return Map.of("deleted", expenseId);
    }

    private Object recordSettlement(OAuth2User principal, JsonNode a) {
        Map<String, Object> group = loadGroup(principal, reqStr(a, "group"));
        String groupId = (String) group.get("groupId");
        Map<String, Object> payer = resolveMember(group, reqStr(a, "from"), true);
        Map<String, Object> receiver = resolveMember(group, reqStr(a, "to"), true);
        if (payer.get("userId").equals(receiver.get("userId"))) {
            throw new McpToolException("from and to must be different people.");
        }
        SettleRequest req = new SettleRequest();
        req.fromUserId = (String) payer.get("userId");
        req.toUserId = (String) receiver.get("userId");
        req.amount = reqDecimal(a, "amount");
        req.date = optStr(a, "date");
        req.note = optStr(a, "note");

        Map<String, Object> created = body(groups.settle(principal, groupId, req));
        Map<String, Object> fresh = groups.getGroup(principal, groupId);

        Map<String, Object> recorded = new LinkedHashMap<>();
        recorded.put("expenseId", created.get("expenseId"));
        recorded.put("from", payer.get("displayName"));
        recorded.put("to", receiver.get("displayName"));
        recorded.put("amount", req.amount);
        recorded.put("currency", group.get("baseCurrency"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recorded", recorded);
        out.put("settleUpPlan", settleUpPlan(fresh));
        return out;
    }

    // ---------- Group admin ----------

    private Object createGroup(OAuth2User principal, JsonNode a) {
        CreateGroupRequest req = new CreateGroupRequest();
        req.name = reqStr(a, "name");
        req.type = reqStr(a, "type");
        req.baseCurrency = reqStr(a, "baseCurrency").toUpperCase();
        req.members = memberInputs(a.get("members"));
        return body(groups.createGroup(principal, req));
    }

    private Object renameGroup(OAuth2User principal, JsonNode a) {
        String groupId = resolveGroupId(principal, reqStr(a, "group"));
        UpdateGroupRequest req = new UpdateGroupRequest();
        req.name = reqStr(a, "name");
        return groups.updateGroup(principal, groupId, req);
    }

    private Object addMembers(OAuth2User principal, JsonNode a) {
        String groupId = resolveGroupId(principal, reqStr(a, "group"));
        AddMembersRequest req = new AddMembersRequest();
        req.members = memberInputs(a.get("members"));
        if (req.members.isEmpty()) throw new McpToolException("members is required");
        return groups.addMembers(principal, groupId, req);
    }

    private Object updateNotes(OAuth2User principal, JsonNode a) {
        String groupId = resolveGroupId(principal, reqStr(a, "group"));
        UpdateNotesRequest req = new UpdateNotesRequest();
        req.notes = a.hasNonNull("notes") ? a.get("notes").asText() : "";
        return groups.updateNotes(principal, groupId, req);
    }

    private Object setFxRate(OAuth2User principal, JsonNode a) {
        String groupId = resolveGroupId(principal, reqStr(a, "group"));
        String code = reqStr(a, "currency").toUpperCase();
        FxRateRequest req = new FxRateRequest();
        req.rate = reqDecimal(a, "rate");
        return groups.setFxRate(principal, groupId, code, req);
    }

    private Object removeFxRate(OAuth2User principal, JsonNode a) {
        String groupId = resolveGroupId(principal, reqStr(a, "group"));
        String code = reqStr(a, "currency").toUpperCase();
        return groups.deleteFxRate(principal, groupId, code);
    }

    private Object deleteGroup(OAuth2User principal, JsonNode a) {
        String groupId = resolveGroupId(principal, reqStr(a, "group"));
        groups.deleteGroup(principal, groupId);
        return Map.of("deleted", groupId);
    }

    // ---------- Lookups ----------

    private String resolveGroupId(OAuth2User principal, String ref) {
        List<Map<String, Object>> all = groups.myGroups(principal);
        for (Map<String, Object> g : all) {
            if (ref.equals(g.get("groupId"))) return (String) g.get("groupId");
        }
        String needle = ref.trim().toLowerCase();
        List<Map<String, Object>> byName = new ArrayList<>();
        for (Map<String, Object> g : all) {
            if (str(g.get("name")).toLowerCase().equals(needle)) byName.add(g);
        }
        List<Map<String, Object>> matches = byName;
        if (matches.isEmpty()) {
            for (Map<String, Object> g : all) {
                if (str(g.get("name")).toLowerCase().contains(needle)) matches.add(g);
            }
        }
        if (matches.size() == 1) return (String) matches.get(0).get("groupId");
        if (matches.size() > 1) {
            throw new McpToolException("\"" + ref + "\" matches several groups: " + names(matches) + ". Be more specific.");
        }
        String available = all.isEmpty() ? "none" : names(all);
        throw new McpToolException("No group matches \"" + ref + "\". Your groups: " + available + ".");
    }

    private Map<String, Object> loadGroup(OAuth2User principal, String ref) {
        return groups.getGroup(principal, resolveGroupId(principal, ref));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveMember(Map<String, Object> group, String ref, boolean activeOnly) {
        List<Map<String, Object>> members = new ArrayList<>();
        for (Map<String, Object> m : (List<Map<String, Object>>) group.get("members")) {
            if (!activeOnly || Boolean.TRUE.equals(m.get("active"))) members.add(m);
        }
        String needle = ref.trim().toLowerCase();
        if (Set.of("me", "i", "you", "myself").contains(needle)) {
            for (Map<String, Object> m : members) {
                if (Boolean.TRUE.equals(m.get("you"))) return m;
            }
        }
        List<Map<String, Object>> exact = new ArrayList<>();
        for (Map<String, Object> m : members) {
            if (ref.equals(m.get("userId"))
                    || str(m.get("displayName")).toLowerCase().equals(needle)
                    || str(m.get("email")).toLowerCase().equals(needle)) {
                exact.add(m);
            }
        }
        List<Map<String, Object>> matches = exact;
        if (matches.isEmpty()) {
            for (Map<String, Object> m : members) {
                if (str(m.get("displayName")).toLowerCase().contains(needle)) matches.add(m);
            }
        }
        if (matches.size() == 1) return matches.get(0);
        String memberNames = memberNames(members);
        if (matches.size() > 1) {
            throw new McpToolException("\"" + ref + "\" matches several members of " + group.get("name")
                    + ": " + memberNames(matches) + ".");
        }
        throw new McpToolException("No " + (activeOnly ? "active " : "") + "member of " + group.get("name")
                + " matches \"" + ref + "\". Members: " + memberNames + ".");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> me(Map<String, Object> group) {
        for (Map<String, Object> m : (List<Map<String, Object>>) group.get("members")) {
            if (Boolean.TRUE.equals(m.get("you"))) return m;
        }
        throw new McpToolException("could not find your membership in this group");
    }

    // ---------- Views ----------

    @SuppressWarnings("unchecked")
    private Map<String, Object> groupSummary(Map<String, Object> group) {
        List<Map<String, Object>> members = new ArrayList<>();
        for (Map<String, Object> m : (List<Map<String, Object>>) group.get("members")) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("userId", m.get("userId"));
            dto.put("name", m.get("displayName"));
            if (!Boolean.TRUE.equals(m.get("placeholder"))) dto.put("email", m.get("email"));
            dto.put("you", m.get("you"));
            dto.put("active", m.get("active"));
            dto.put("net", m.get("net"));
            members.add(dto);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupId", group.get("groupId"));
        out.put("name", group.get("name"));
        out.put("type", group.get("type"));
        out.put("baseCurrency", group.get("baseCurrency"));
        out.put("fxRates", group.get("fxRates"));
        out.put("members", members);
        out.put("balanceNote", "net is in " + group.get("baseCurrency")
                + ": positive = the group owes them, negative = they owe the group.");
        out.put("settleUpPlan", settleUpPlan(group));
        out.put("expenseCount", expensesOf(group).size());
        out.put("notes", group.get("notes"));
        return out;
    }

    private Map<String, Object> expenseView(Map<String, Object> group, Map<String, Object> e) {
        Map<String, Object> shares = sharesOf(e);
        String paidBy = (String) e.get("paidByUserId");
        double cost = num(e.get("cost"));
        Map<String, Object> owed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : shares.entrySet()) {
            String id = entry.getKey();
            double net = num(entry.getValue());
            double o = (id.equals(paidBy) ? cost : 0) - net;
            if (Math.abs(o) > 0.004) owed.put(nameOf(group, id), round2(o));
        }
        if (paidBy != null && !shares.containsKey(paidBy)) {
            owed.put(nameOf(group, paidBy), round2(cost));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("expenseId", e.get("expenseId"));
        out.put("date", e.get("date"));
        out.put("description", e.get("description"));
        out.put("category", e.get("category"));
        out.put("settlement", isSettlement(e));
        out.put("cost", e.get("cost"));
        out.put("currency", e.get("currency"));
        out.put("paidBy", nameOf(group, paidBy));
        out.put("owedBy", owed);
        out.put("splitMode", e.get("splitMode"));
        if (e.get("notes") != null && !str(e.get("notes")).isEmpty()) out.put("notes", e.get("notes"));
        out.put("hasReceipt", e.get("receiptKey") != null);
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> settleUpPlan(Map<String, Object> group) {
        List<Map<String, Object>> plan = new ArrayList<>();
        Object settlement = group.get("settlement");
        if (settlement instanceof List<?> rows) {
            for (Object row : rows) {
                Map<String, Object> s = (Map<String, Object>) row;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("from", s.get("fromName"));
                r.put("to", s.get("toName"));
                r.put("amount", s.get("amount"));
                plan.add(r);
            }
        }
        return plan;
    }

    // ---------- Split building ----------

    private record Split(String mode, List<Participant> participants) {}

    @SuppressWarnings("unchecked")
    private Split buildSplit(Map<String, Object> group, JsonNode splitNode, Set<String> allowedIds) {
        String mode = splitNode != null && splitNode.hasNonNull("mode") ? splitNode.get("mode").asText() : "equal";
        JsonNode entries = splitNode == null ? null : splitNode.get("participants");

        List<Participant> participants = new ArrayList<>();
        if (entries == null || !entries.isArray() || entries.isEmpty()) {
            if (!mode.equals("equal")) {
                throw new McpToolException("participants are required for percent and amount splits.");
            }
            for (Map<String, Object> m : (List<Map<String, Object>>) group.get("members")) {
                if (Boolean.TRUE.equals(m.get("active"))) {
                    Participant p = new Participant();
                    p.userId = (String) m.get("userId");
                    p.value = null;
                    participants.add(p);
                }
            }
            return new Split(mode, participants);
        }

        for (JsonNode entry : entries) {
            String memberRef = entry.path("member").asText();
            String userId;
            if (allowedIds != null && allowedIds.contains(memberRef)) {
                userId = memberRef;
            } else {
                userId = (String) resolveMember(group, memberRef, allowedIds == null).get("userId");
            }
            Participant p = new Participant();
            p.userId = userId;
            if (!mode.equals("equal")) {
                if (!entry.hasNonNull("value")) {
                    throw new McpToolException(memberRef + " needs a value for a " + mode + " split.");
                }
                p.value = entry.get("value").decimalValue();
            } else {
                p.value = null;
            }
            participants.add(p);
        }
        return new Split(mode, participants);
    }

    private void applySplit(CreateExpenseRequest req, Split split) {
        req.splitMode = split.mode();
        req.participants = split.participants();
    }

    private List<MemberInput> memberInputs(JsonNode node) {
        List<MemberInput> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode m : node) {
                MemberInput mi = new MemberInput();
                mi.displayName = m.hasNonNull("name") ? m.get("name").asText() : "";
                mi.email = m.hasNonNull("email") ? m.get("email").asText() : "";
                out.add(mi);
            }
        }
        return out;
    }

    // ---------- small helpers ----------

    private String nameOf(Map<String, Object> group, String userId) {
        if (userId == null) return null;
        for (Map<String, Object> m : membersOf(group)) {
            if (userId.equals(m.get("userId"))) return str(m.get("displayName"));
        }
        return userId;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> membersOf(Map<String, Object> group) {
        Object members = group.get("members");
        return members instanceof List ? (List<Map<String, Object>>) members : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> expensesOf(Map<String, Object> group) {
        Object expenses = group.get("expenses");
        return expenses instanceof List ? (List<Map<String, Object>>) expenses : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sharesOf(Map<String, Object> e) {
        Object shares = e.get("shares");
        return shares instanceof Map ? (Map<String, Object>) shares : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> splitValuesOf(Map<String, Object> e) {
        Object values = e.get("splitValues");
        return values instanceof Map ? (Map<String, Object>) values : Map.of();
    }

    private Map<String, Object> findExpense(Map<String, Object> group, String expenseId) {
        if (expenseId == null) return null;
        for (Map<String, Object> e : expensesOf(group)) {
            if (expenseId.equals(e.get("expenseId"))) return e;
        }
        return null;
    }

    private boolean isSettlement(Map<String, Object> e) {
        return Boolean.TRUE.equals(e.get("payment")) || "Payment".equals(e.get("category"));
    }

    private String names(List<Map<String, Object>> groups) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> g : groups) names.add("\"" + str(g.get("name")) + "\"");
        return String.join(", ", names);
    }

    private String memberNames(List<Map<String, Object>> members) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> m : members) names.add(str(m.get("displayName")));
        return String.join(", ", names);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> response) {
        Object b = response.getBody();
        if (b == null) return Map.of();
        if (b instanceof Map) return (Map<String, Object>) b;
        return Map.of("value", b);
    }

    private static boolean containsCi(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase().contains(needleLower);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static double num(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(o.toString());
    }

    private static BigDecimal round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String reqStr(JsonNode a, String field) {
        if (a == null || !a.hasNonNull(field) || a.get(field).asText().isEmpty()) {
            throw new McpToolException(field + " is required");
        }
        return a.get(field).asText();
    }

    private static String optStr(JsonNode a, String field) {
        return a != null && a.hasNonNull(field) ? a.get(field).asText() : null;
    }

    private static BigDecimal reqDecimal(JsonNode a, String field) {
        if (a == null || !a.hasNonNull(field)) throw new McpToolException(field + " is required");
        JsonNode n = a.get(field);
        if (n.isNumber()) return n.decimalValue();
        try {
            return new BigDecimal(n.asText());
        } catch (NumberFormatException e) {
            throw new McpToolException(field + " must be a number");
        }
    }

    // ---------- Tool definitions (JSON Schema) ----------

    public List<Map<String, Object>> definitions() {
        if (cachedDefinitions == null) cachedDefinitions = buildDefinitions();
        return cachedDefinitions;
    }

    private List<Map<String, Object>> buildDefinitions() {
        List<Map<String, Object>> defs = new ArrayList<>();

        defs.add(tool("whoami", "Current user", true, false,
                "The SplitForce account this token belongs to.",
                schema(List.of())));

        defs.add(tool("list_groups", "List groups", true, false,
                "All groups you belong to, with member/expense counts, last activity and your net balance in each (base currency; positive = you are owed).",
                schema(List.of())));

        defs.add(tool("get_group", "Group details", true, false,
                "Members with net balances, the minimum-payments settle-up plan, FX rates and notes for one group. Use list_expenses for the expenses themselves.",
                schema(List.of("group"), groupProp())));

        defs.add(tool("list_expenses", "List expenses", true, false,
                "Expenses and settlements in a group, newest first, with who paid and who owes what. All filters are optional and combine.",
                schema(List.of("group"),
                        groupProp(),
                        prop("person", "string", "Only expenses this member paid for or has a share of"),
                        prop("paidBy", "string", "Only expenses this member paid"),
                        prop("category", "string", "Exact category, e.g. \"Dining out\" or \"Payment\" for settlements"),
                        prop("from", "string", "On or after this date (YYYY-MM-DD)"),
                        prop("to", "string", "On or before this date (YYYY-MM-DD)"),
                        prop("search", "string", "Case-insensitive match on description, category or notes"),
                        prop("includeSettlements", "boolean", "Include settlement payments (default true)"),
                        prop("limit", "integer", "Max expenses to return (1-500, default 50)"))));

        defs.add(tool("list_contacts", "List contacts", true, false,
                "People you share groups with (useful for adding them to a new group by email).",
                schema(List.of())));

        defs.add(tool("export_group_csv", "Export group CSV", true, false,
                "The whole group as CSV: one row per expense with each member's net in the base currency.",
                schema(List.of("group"), groupProp())));

        defs.add(tool("add_expense", "Add expense", false, false,
                "Log an expense in a group. Defaults: paid by you, today, base currency, split equally across all active members.",
                schema(List.of("group", "description", "cost"),
                        groupProp(),
                        prop("description", "string", "What the expense was for (max 140 chars)"),
                        prop("cost", "number", "Total cost (positive)"),
                        currencyProp(),
                        prop("date", "string", "YYYY-MM-DD; defaults to today"),
                        prop("category", "string", "e.g. \"Dining out\", \"Groceries\", \"Rideshare / Taxis\", \"Hotel\""),
                        prop("notes", "string", "Freeform notes (max 1000 chars)"),
                        prop("paidBy", "string", "Member who paid; defaults to you"),
                        splitProp())));

        defs.add(tool("update_expense", "Edit expense", false, false,
                "Change any fields of an existing expense; omitted fields keep their current value. Giving split replaces the whole split.",
                schema(List.of("group", "expenseId"),
                        groupProp(),
                        prop("expenseId", "string", "Id of the expense to edit"),
                        prop("description", "string", "Max 140 chars"),
                        prop("cost", "number", "Total cost (positive)"),
                        currencyProp(),
                        prop("date", "string", "YYYY-MM-DD"),
                        prop("category", "string", "Empty string clears it"),
                        prop("notes", "string", "Empty string clears it"),
                        prop("paidBy", "string", "Member who paid"),
                        splitProp())));

        defs.add(tool("delete_expense", "Delete expense", false, true,
                "Permanently delete an expense or settlement. Confirm with the user first.",
                schema(List.of("group", "expenseId"),
                        groupProp(),
                        prop("expenseId", "string", "Id of the expense to delete"))));

        defs.add(tool("record_settlement", "Record settlement", false, false,
                "Record that one member paid another back (e.g. a step from the settle-up plan in get_group). Settled in the group base currency.",
                schema(List.of("group", "from", "to", "amount"),
                        groupProp(),
                        prop("from", "string", "Who paid"),
                        prop("to", "string", "Who received the money"),
                        prop("amount", "number", "Amount paid (positive, base currency)"),
                        prop("date", "string", "YYYY-MM-DD; defaults to today"),
                        prop("note", "string", "Optional note (max 140 chars)"))));

        defs.add(tool("create_group", "Create group", false, false,
                "Create a group. You are added automatically; list the other members.",
                schema(List.of("name", "type", "baseCurrency"),
                        prop("name", "string", "Group name (1-80 chars)"),
                        enumProp("type", "Group type", List.of("trip", "home", "general")),
                        prop("baseCurrency", "string", "ISO 3-letter currency code"),
                        membersProp("Other members to add"))));

        defs.add(tool("rename_group", "Rename group", false, false,
                "Change a group's name.",
                schema(List.of("group", "name"),
                        groupProp(),
                        prop("name", "string", "New name (1-80 chars)"))));

        defs.add(tool("add_members", "Add members", false, false,
                "Add people to a group. Members already in it are skipped.",
                schema(List.of("group", "members"),
                        groupProp(),
                        membersProp("People to add"))));

        defs.add(tool("update_group_notes", "Update group notes", false, false,
                "Replace the group's shared notes (plain text or markdown, up to 10,000 characters). Read the current notes with get_group first if appending.",
                schema(List.of("group", "notes"),
                        groupProp(),
                        prop("notes", "string", "New notes (max 10000 chars)"))));

        defs.add(tool("set_fx_rate", "Set FX rate", false, false,
                "Set how many units of the group's base currency one unit of another currency is worth (e.g. USD -> 84.5 in an INR group). Needed before logging expenses in that currency.",
                schema(List.of("group", "currency", "rate"),
                        groupProp(),
                        prop("currency", "string", "ISO 3-letter currency code"),
                        prop("rate", "number", "Units of base currency per 1 unit of this currency (positive)"))));

        defs.add(tool("remove_fx_rate", "Remove FX rate", false, true,
                "Remove a currency from a group. Fails if any expense still uses it.",
                schema(List.of("group", "currency"),
                        groupProp(),
                        prop("currency", "string", "ISO 3-letter currency code"))));

        defs.add(tool("delete_group", "Delete group", false, true,
                "Permanently delete a group with all its expenses. Only the owner can, and only once everyone is settled up. Confirm with the user first.",
                schema(List.of("group"), groupProp())));

        return defs;
    }

    private static Map<String, Object> tool(String name, String title, boolean readOnly, boolean destructive,
                                            String description, Map<String, Object> inputSchema) {
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("title", title);
        annotations.put("readOnlyHint", readOnly);
        annotations.put("destructiveHint", destructive);
        annotations.put("openWorldHint", false);
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("title", title);
        t.put("description", description);
        t.put("inputSchema", inputSchema);
        t.put("annotations", annotations);
        return t;
    }

    @SafeVarargs
    private static Map<String, Object> schema(List<String> required, Map<String, Object>... props) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map<String, Object> p : props) properties.putAll(p);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }

    private static Map<String, Object> prop(String name, String type, String description) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", type);
        spec.put("description", description);
        return Map.of(name, spec);
    }

    private static Map<String, Object> enumProp(String name, String description, List<String> values) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", "string");
        spec.put("description", description);
        spec.put("enum", values);
        return Map.of(name, spec);
    }

    private static Map<String, Object> groupProp() {
        return prop("group", "string", "Group id or (part of) the group name");
    }

    private static Map<String, Object> currencyProp() {
        return prop("currency", "string",
                "ISO currency code; defaults to the group base currency. Other currencies need an FX rate on the group.");
    }

    private static Map<String, Object> splitProp() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", "number");
        value.put("description", "Percent (percent mode) or amount owed (amount mode); omit for equal");
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("type", "string");
        member.put("description", "Member name, email, user id, or \"me\"");
        Map<String, Object> participantProps = new LinkedHashMap<>();
        participantProps.put("member", member);
        participantProps.put("value", value);
        Map<String, Object> participantItem = new LinkedHashMap<>();
        participantItem.put("type", "object");
        participantItem.put("properties", participantProps);
        participantItem.put("required", List.of("member"));
        Map<String, Object> participants = new LinkedHashMap<>();
        participants.put("type", "array");
        participants.put("items", participantItem);
        participants.put("description", "Who shares the cost. Omit for an equal split across all active members.");
        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put("type", "string");
        mode.put("enum", List.of("equal", "percent", "amount"));
        mode.put("description", "How the cost is shared (default equal). Percentages must sum to 100; amounts must sum to the cost.");
        Map<String, Object> splitProps = new LinkedHashMap<>();
        splitProps.put("mode", mode);
        splitProps.put("participants", participants);
        Map<String, Object> split = new LinkedHashMap<>();
        split.put("type", "object");
        split.put("properties", splitProps);
        split.put("description", "How the cost is shared across members.");
        return Map.of("split", split);
    }

    private static Map<String, Object> membersProp(String description) {
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("type", "string");
        name.put("description", "Display name (max 60 chars)");
        Map<String, Object> email = new LinkedHashMap<>();
        email.put("type", "string");
        email.put("description", "Links the member to their Google account so they see the group");
        Map<String, Object> memberProps = new LinkedHashMap<>();
        memberProps.put("name", name);
        memberProps.put("email", email);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("properties", memberProps);
        Map<String, Object> members = new LinkedHashMap<>();
        members.put("type", "array");
        members.put("items", item);
        members.put("description", description + " (each needs a name or an email).");
        return Map.of("members", members);
    }
}
