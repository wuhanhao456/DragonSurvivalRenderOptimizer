package top.wu949.dsbr.client.bedrock.pojo;


import top.wu949.dsbr.DragonSurvivalBedrockRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;

@OnlyIn(Dist.CLIENT)
public enum BedrockVersion {
    /**
     * 旧版本基岩版模型，仅限 1.10.0
     */
    LEGACY("1.10.0"),
    /**
     * 新版本基岩版模型，往后的 1.14.0，1.16.0 1.21.0 通通用此版本读取
     */
    NEW("1.12.0");

    private final String version;

    BedrockVersion(final String version) {
        this.version = version;
    }

    public static boolean isNewVersion(final BedrockModelPOJO bedrockModel) {
        return compareVersion(bedrockModel.getFormatVersion(), NEW.version) >= 0;
    }

    public static boolean isLegacyVersion(final BedrockModelPOJO bedrockModel) {
        return compareVersion(bedrockModel.getFormatVersion(), LEGACY.version) == 0;
    }

    private static int compareVersion(final String left, final String right) {
        try {
            String[] leftParts = left.split("\\.");
            String[] rightParts = right.split("\\.");
            int maxLength = Math.max(leftParts.length, rightParts.length);

            for (int index = 0; index < maxLength; index++) {
                int leftValue = index < leftParts.length ? Integer.parseInt(leftParts[index]) : 0;
                int rightValue = index < rightParts.length ? Integer.parseInt(rightParts[index]) : 0;

                if (leftValue != rightValue) {
                    return Integer.compare(leftValue, rightValue);
                }
            }

            return 0;
        } catch (NumberFormatException exception) {
            DragonSurvivalBedrockRenderer.LOGGER.error("DSBR 无法解析 Bedrock 模型版本号: left={}, right={}", left, right, exception);
            throw exception;
        }
    }

    public static BedrockVersion getVersion(final BedrockModelPOJO pojo) throws InvalidVersionSpecificationException {
        if (isNewVersion(pojo)) {
            return NEW;
        } else if (isLegacyVersion(pojo)) {
            return LEGACY;
        }

        throw new InvalidVersionSpecificationException("Invalid version for model: " + pojo);
    }
}
