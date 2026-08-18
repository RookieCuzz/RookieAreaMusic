package io.github.rookiecuzz.rookieregions.api;

import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.provider.RegionProvider;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.StateFlag;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RookieRegionsBootstrapTest {
    @Test
    void dependentPluginsRegisterFlagsAndProvidersBeforeFreeze() {
        Plugin host = plugin("RookieRegions");
        Plugin extension = plugin("ExampleExtension");
        RookieRegionsBootstrap bootstrap = new RookieRegionsBootstrap(
                host, ProtectionFlags.REGISTRY.values()
        );
        StateFlag custom = new StateFlag("example.flight");
        RegionProvider provider = provider("example_claims");

        bootstrap.registerFlag(extension, custom);
        bootstrap.registerProvider(extension, provider);
        RookieRegionsBootstrap.Snapshot frozen = bootstrap.freeze(host);

        assertSame(custom, frozen.flags().require("example.flight"));
        assertSame(provider, frozen.providers().get("example_claims"));
        assertFalse(bootstrap.acceptingRegistrations());
        assertThrows(
                IllegalStateException.class,
                () -> bootstrap.registerFlag(extension, new StateFlag("example.late"))
        );
    }

    @Test
    void reservedAndDuplicateProviderIdsAreRejected() {
        Plugin host = plugin("RookieRegions");
        Plugin extension = plugin("ExampleExtension");
        RookieRegionsBootstrap bootstrap = new RookieRegionsBootstrap(host, List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> bootstrap.registerProvider(extension, provider("worldguard"))
        );
        bootstrap.registerProvider(extension, provider("example"));
        assertThrows(
                IllegalArgumentException.class,
                () -> bootstrap.registerProvider(extension, provider("example"))
        );
        assertTrue(bootstrap.acceptingRegistrations());
    }

    private static RegionProvider provider(String id) {
        return new RegionProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public RegionSnapshot snapshot() {
                return RegionSnapshot.empty();
            }
        };
    }

    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> switch(method.getName()) {
                    case "getName" -> name;
                    case "toString" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if(!type.isPrimitive()) {
            return null;
        }
        if(type == boolean.class) {
            return false;
        }
        if(type == char.class) {
            return '\0';
        }
        return 0;
    }
}
