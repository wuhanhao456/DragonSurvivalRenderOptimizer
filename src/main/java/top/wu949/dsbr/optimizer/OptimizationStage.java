package top.wu949.dsbr.optimizer;
/** Test-only cost isolation. Normal clients always use all five stages; stage 5 adds same-frame re-render pose reuse. */
public final class OptimizationStage {
    public static final int VALUE = Integer.getInteger("dsbr.validationStage", 5);
    private OptimizationStage() {}
}
