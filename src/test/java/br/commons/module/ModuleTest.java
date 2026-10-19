package br.commons.module;

import br.commons.Result;
import br.commons.business.BusinessError;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleTest {

    private final List<String> events = new ArrayList<>();

    private Lifecycle recording(String name, boolean initializes, boolean terminates) {
        return new Lifecycle() {
            @Override public Result<Void, BusinessError> initialize() {
                events.add("init " + name);
                return initializes ? Result.success() : Result.failure(new BusinessError.Infrastructure(name));
            }
            @Override public Result<Void, BusinessError> terminate() {
                events.add("term " + name);
                return terminates ? Result.success() : Result.failure(new BusinessError.Infrastructure(name));
            }
        };
    }

    private Module module(String name) { return Module.of(recording(name, true, true)); }

    @Test
    void dependenciesFirst_terminateReversed() {
        val root = module("root");
        root.attach(module("a"));
        root.attach(module("b"));

        assertTrue(root.initialize().isSuccess());
        assertTrue(root.terminate().isSuccess());
        assertEquals(List.of("init a", "init b", "init root", "term root", "term b", "term a"), events);
    }

    @Test
    void failureInTheMiddle_terminatesOnlyInitialized() {
        val root = module("root");
        root.attach(module("a"));
        root.attach(Module.of(recording("b", false, true)));
        root.attach(module("c"));

        assertTrue(root.initialize().isFailure());
        assertTrue(root.terminate().isSuccess());
        assertEquals(List.of("init a", "init b", "term a"), events);
    }

    @Test
    void failureInsideDependency_terminatesItsInitializedChildren() {
        val child = module("child");
        val dependency = Module.of(recording("dependency", false, true));
        dependency.attach(child);
        val root = module("root");
        root.attach(dependency);

        assertTrue(root.initialize().isFailure());
        root.terminate();
        assertEquals(List.of("init child", "init dependency", "term child"), events);
    }

    @Test
    void terminateFailure_goesOnAndReturnsFirst() {
        val root = Module.of(recording("root", true, false));
        root.attach(Module.of(recording("a", true, false)));
        root.attach(module("b"));

        assertTrue(root.initialize().isSuccess());
        val result = root.terminate();
        assertTrue(result.isFailure());
        assertEquals(List.of("init a", "init b", "init root", "term root", "term b", "term a"), events);
    }

    @Test
    void terminateTwice_terminatesOnce() {
        val root = module("root");
        root.attach(module("a"));
        root.initialize();
        root.terminate();
        root.terminate();
        assertEquals(List.of("init a", "init root", "term root", "term a"), events);
    }
}
