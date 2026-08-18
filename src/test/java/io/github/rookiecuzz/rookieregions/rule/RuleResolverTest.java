package io.github.rookiecuzz.rookieregions.rule;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionDomain;
import io.github.rookiecuzz.rookieregions.core.RegionGraph;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleResolverTest {
    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "minecraft:overworld"
    );
    private final UUID parentOwner = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private final UUID parentMember = UUID.fromString(
            "10000000-0000-0000-0000-000000000002"
    );
    private final UUID childOwner = UUID.fromString(
            "10000000-0000-0000-0000-000000000003"
    );
    private final UUID childMember = UUID.fromString(
            "10000000-0000-0000-0000-000000000004"
    );
    private final UUID globalOwner = UUID.fromString(
            "10000000-0000-0000-0000-000000000005"
    );
    private final UUID globalMember = UUID.fromString(
            "10000000-0000-0000-0000-000000000006"
    );

    @Test
    void ownershipDefaultsInheritAncestorOwnersButNotAncestorMembers(){
        Region global = Region.builder(
                        RegionKey.global(world), GlobalShape.INSTANCE
                )
                .owners(RegionDomain.builder().player(globalOwner).build())
                .members(RegionDomain.builder().player(globalMember).build())
                .build();
        Region parent = region(
                "parent",
                global.key(),
                0,
                0,
                100,
                RegionDomain.builder().player(parentOwner).build(),
                RegionDomain.builder().player(parentMember).build()
        );
        Region child = region(
                "child",
                parent.key(),
                0,
                10,
                20,
                RegionDomain.builder().player(childOwner).build(),
                RegionDomain.builder().player(childMember).build()
        );
        RuleResolver resolver = resolver(global, parent, child);

        assertDecision(resolver, child, parentOwner, State.ALLOW);
        assertDecision(resolver, child, childOwner, State.ALLOW);
        assertDecision(resolver, child, childMember, State.ALLOW);
        assertDecision(resolver, child, parentMember, State.DENY);
        assertDecision(resolver, child, globalOwner, State.ALLOW);
        assertDecision(resolver, child, globalMember, State.DENY);
        assertEquals(
                true,
                RegionGraph.of(List.of(global, parent, child)).hasInheritedOwner(
                        child.key(), globalOwner, Set.of()
                )
        );
        assertEquals(
                Association.MEMBER,
                resolver.association(parent, Subject.player(parentMember))
        );

        RuleResolution<State> wilderness = resolver.resolve(
                ProtectionFlags.BUILD,
                world,
                List.of(),
                Subject.none()
        );
        assertEquals(State.ALLOW, wilderness.value().orElseThrow());
    }

    @Test
    void childExplicitValueBeatsParentAndGlobalBeforeLeafPriorityConflicts(){
        Region global = Region.builder(RegionKey.global(world), GlobalShape.INSTANCE)
                .flag(ProtectionFlags.BUILD, State.DENY)
                .build();
        Region parent = Region.builder(
                        new RegionKey(world, "parent"),
                        new CuboidShape(0, 0, 0, 100, 100, 100)
                )
                .parent(global.key())
                .priority(100)
                .flag(ProtectionFlags.BUILD, State.DENY)
                .build();
        Region child = Region.builder(
                        new RegionKey(world, "child"),
                        new CuboidShape(10, 10, 10, 20, 20, 20)
                )
                .parent(parent.key())
                .priority(-100)
                .flag(ProtectionFlags.BUILD, State.ALLOW)
                .build();
        RuleResolver resolver = resolver(global, parent, child);

        RuleResolution<State> result = resolver.resolve(
                ProtectionFlags.BUILD,
                world,
                List.of(parent.key(), child.key()),
                Subject.none()
        );
        assertEquals(State.ALLOW, result.value().orElseThrow());
        assertEquals(child.key(), result.contributions().getFirst().source());
        assertEquals(ValueOrigin.LOCAL_EXPLICIT, result.contributions().getFirst().origin());
    }

    @Test
    void unrelatedLeavesUsePriorityAndDenyWinsAtEqualPriority(){
        Region global = global();
        Region allow = Region.builder(
                        new RegionKey(world, "allow"),
                        new CuboidShape(0, 0, 0, 20, 20, 20)
                )
                .parent(global.key())
                .priority(5)
                .flag(ProtectionFlags.BUILD, State.ALLOW)
                .build();
        Region deny = Region.builder(
                        new RegionKey(world, "deny"),
                        new CuboidShape(0, 0, 0, 20, 20, 20)
                )
                .parent(global.key())
                .priority(5)
                .flag(ProtectionFlags.BUILD, State.DENY)
                .build();
        RuleResolver resolver = resolver(global, allow, deny);

        RuleResolution<State> tie = resolver.resolve(
                ProtectionFlags.BUILD,
                world,
                List.of(allow.key(), deny.key()),
                Subject.none()
        );
        assertEquals(ResolutionStatus.RESOLVED_CONFLICT, tie.status());
        assertEquals(State.DENY, tie.value().orElseThrow());

        Region higherAllow = Region.builder(
                        new RegionKey(world, "higher"),
                        new CuboidShape(0, 0, 0, 20, 20, 20)
                )
                .parent(global.key())
                .priority(6)
                .flag(ProtectionFlags.BUILD, State.ALLOW)
                .build();
        RuleResolver priorityResolver = resolver(global, deny, higherAllow);
        assertEquals(
                State.ALLOW,
                priorityResolver.resolve(
                        ProtectionFlags.BUILD,
                        world,
                        List.of(deny.key(), higherAllow.key()),
                        Subject.none()
                ).value().orElseThrow()
        );
    }

    @Test
    void globalIsFallbackAndAllowPlayerRegionsIsDirectOnlyAndDenyByDefault(){
        Region global = Region.builder(RegionKey.global(world), GlobalShape.INSTANCE)
                .flag(ProtectionFlags.BUILD, State.ALLOW)
                .flag(ProtectionFlags.ALLOW_PLAYER_REGIONS, State.ALLOW)
                .build();
        Region parent = region(
                "parent",
                global.key(),
                0,
                0,
                100,
                RegionDomain.empty(),
                RegionDomain.empty()
        );
        RuleResolver resolver = resolver(global, parent);

        assertEquals(
                State.ALLOW,
                resolver.resolveForRegion(
                        ProtectionFlags.BUILD,
                        parent.key(),
                        Subject.none()
                ).value().orElseThrow()
        );
        assertEquals(
                State.DENY,
                resolver.resolveForRegion(
                        ProtectionFlags.ALLOW_PLAYER_REGIONS,
                        parent.key(),
                        Subject.none()
                ).value().orElseThrow()
        );
        assertEquals(
                State.ALLOW,
                resolver.resolveForRegion(
                        ProtectionFlags.ALLOW_PLAYER_REGIONS,
                        global.key(),
                        Subject.none()
                ).value().orElseThrow()
        );
        assertEquals(State.ALLOW, defaultDecision(resolver, ProtectionFlags.PVP, parent));
        assertEquals(State.ALLOW, defaultDecision(resolver, ProtectionFlags.ENTRY, parent));
        assertEquals(State.ALLOW, defaultDecision(resolver, ProtectionFlags.EXPLOSION, parent));
    }

    @Test
    void blockBreakAndPlaceFallBackToBuildOnlyWhenUnset(){
        Region global = global();
        Region protectedRegion = region(
                "protected",
                global.key(),
                0,
                0,
                100,
                RegionDomain.empty(),
                RegionDomain.empty()
        );
        var applicable = new RegionQuery(RegionSnapshot.of(
                1L,
                List.of(global, protectedRegion)
        )).at(world, 10, 10, 10);

        assertEquals(
                State.DENY,
                ProtectionRuleSet.resolveBuildAction(
                        applicable,
                        Subject.none(),
                        BuildAction.BREAK
                ).value().orElseThrow()
        );

        Region explicitBreak = Region.builder(
                        protectedRegion.key(),
                        protectedRegion.shape()
                )
                .parent(global.key())
                .flag(ProtectionFlags.BLOCK_BREAK, State.ALLOW)
                .build();
        var explicitApplicable = new RegionQuery(RegionSnapshot.of(
                2L,
                List.of(global, explicitBreak)
        )).at(world, 10, 10, 10);
        assertEquals(
                State.ALLOW,
                ProtectionRuleSet.resolveBuildAction(
                        explicitApplicable,
                        Subject.none(),
                        BuildAction.BREAK
                ).value().orElseThrow()
        );
        assertEquals(
                State.DENY,
                ProtectionRuleSet.resolveBuildAction(
                        explicitApplicable,
                        Subject.none(),
                        BuildAction.PLACE
                ).value().orElseThrow()
        );
    }

    @Test
    void containerFallsBackToUseOnlyWhenContainerIsUnset(){
        Region global = global();
        Region deniedUse = Region.builder(
                        new RegionKey(world, "use-denied"),
                        new CuboidShape(0, 0, 0, 20, 20, 20)
                )
                .parent(global.key())
                .flag(ProtectionFlags.USE, State.DENY)
                .build();
        var inherited = new RegionQuery(RegionSnapshot.of(
                1L, List.of(global, deniedUse)
        )).at(world, 10, 10, 10);
        assertEquals(
                State.DENY,
                ProtectionRuleSet.resolveContainer(inherited, Subject.none())
                        .value().orElseThrow()
        );

        Region explicitContainer = Region.builder(
                        deniedUse.key(), deniedUse.shape()
                )
                .parent(global.key())
                .flag(ProtectionFlags.USE, State.DENY)
                .flag(ProtectionFlags.CONTAINER, State.ALLOW)
                .build();
        var overridden = new RegionQuery(RegionSnapshot.of(
                2L, List.of(global, explicitContainer)
        )).at(world, 10, 10, 10);
        assertEquals(
                State.ALLOW,
                ProtectionRuleSet.resolveContainer(overridden, Subject.none())
                        .value().orElseThrow()
        );
        RegionQuery query = new RegionQuery(RegionSnapshot.of(
                2L, List.of(global, explicitContainer)
        ));
        assertEquals(
                true,
                query.allowsContainer(world, 10, 10, 10, Subject.none())
        );
        assertEquals(
                false,
                query.allowsBuild(
                        world, 10, 10, 10, Subject.none(), BuildAction.BREAK
                )
        );
    }

    @Test
    void actionFallbackIsResolvedPerLeafBeforePriorityAndConflict(){
        Region global = global();
        Region lowSpecificAllow = Region.builder(
                        new RegionKey(world, "specific-allow"),
                        new CuboidShape(0, 0, 0, 20, 20, 20)
                )
                .parent(global.key())
                .priority(1)
                .flag(ProtectionFlags.BLOCK_BREAK, State.ALLOW)
                .flag(ProtectionFlags.CONTAINER, State.ALLOW)
                .build();
        Region highFallbackDeny = Region.builder(
                        new RegionKey(world, "fallback-deny"),
                        new CuboidShape(0, 0, 0, 20, 20, 20)
                )
                .parent(global.key())
                .priority(10)
                .flag(ProtectionFlags.BUILD, State.DENY)
                .flag(ProtectionFlags.USE, State.DENY)
                .build();
        var applicable = new RegionQuery(RegionSnapshot.of(
                1L, List.of(global, lowSpecificAllow, highFallbackDeny)
        )).at(world, 10, 10, 10);

        RuleResolution<State> breakDecision =
                ProtectionRuleSet.resolveBuildAction(
                        applicable, Subject.none(), BuildAction.BREAK
                );
        RuleResolution<State> containerDecision =
                ProtectionRuleSet.resolveContainer(applicable, Subject.none());

        assertEquals(State.DENY, breakDecision.value().orElseThrow());
        assertEquals(
                highFallbackDeny.key(),
                breakDecision.contributions().getFirst().leaf()
        );
        assertEquals(State.DENY, containerDecision.value().orElseThrow());
        assertEquals(
                highFallbackDeny.key(),
                containerDecision.contributions().getFirst().leaf()
        );
    }

    @Test
    void equalPriorityFallbackDenyConflictsWithSpecificAllowAndWins(){
        Region global = global();
        Region specificAllow = Region.builder(
                        new RegionKey(world, "specific-tie"),
                        new CuboidShape(0, 0, 0, 20, 20, 20)
                )
                .parent(global.key())
                .priority(5)
                .flag(ProtectionFlags.BLOCK_PLACE, State.ALLOW)
                .build();
        Region fallbackDeny = Region.builder(
                        new RegionKey(world, "fallback-tie"),
                        new CuboidShape(0, 0, 0, 20, 20, 20)
                )
                .parent(global.key())
                .priority(5)
                .flag(ProtectionFlags.BUILD, State.DENY)
                .build();
        var applicable = new RegionQuery(RegionSnapshot.of(
                1L, List.of(global, specificAllow, fallbackDeny)
        )).at(world, 10, 10, 10);

        RuleResolution<State> result = ProtectionRuleSet.resolveBuildAction(
                applicable, Subject.none(), BuildAction.PLACE
        );

        assertEquals(ResolutionStatus.RESOLVED_CONFLICT, result.status());
        assertEquals(State.DENY, result.value().orElseThrow());
        assertEquals(2, result.contributions().size());
    }

    private void assertDecision(RuleResolver resolver,
                                Region leaf,
                                UUID player,
                                State expected){
        RuleResolution<State> result = resolver.resolve(
                ProtectionFlags.BUILD,
                world,
                List.of(leaf.key()),
                Subject.player(player)
        );
        assertEquals(expected, result.value().orElseThrow());
    }

    private State defaultDecision(RuleResolver resolver,
                                  Flag<State> flag,
                                  Region region){
        return resolver.resolveForRegion(flag, region.key(), Subject.none())
                .value()
                .orElseThrow();
    }

    private RuleResolver resolver(Region... regions){
        return new RuleResolver(RegionGraph.of(List.of(regions)));
    }

    private Region global(){
        return Region.builder(RegionKey.global(world), GlobalShape.INSTANCE).build();
    }

    private Region region(String id,
                          RegionKey parent,
                          int priority,
                          double min,
                          double max,
                          RegionDomain owners,
                          RegionDomain members){
        return Region.builder(
                        new RegionKey(world, id),
                        new CuboidShape(min, min, min, max, max, max)
                )
                .parent(parent)
                .priority(priority)
                .owners(owners)
                .members(members)
                .build();
    }
}
