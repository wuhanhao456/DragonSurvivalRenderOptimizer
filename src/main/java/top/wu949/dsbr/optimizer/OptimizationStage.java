package top.wu949.dsbr.optimizer;
/** Test-only cost isolation. Normal clients always use all four stages. */
public final class OptimizationStage {
    public static final int VALUE = Integer.getInteger("dsbr.validationStage", 4);
    private OptimizationStage() {}
}
