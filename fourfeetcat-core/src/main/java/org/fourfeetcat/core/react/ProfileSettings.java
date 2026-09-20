package org.fourfeetcat.core.react;

import org.fourfeetcat.core.profile.Profile;

/**
 * 运行参数的读取与回落（课件约定：最大轮数默认 10、历史截断默认 20）。
 *
 * <p>包内私有：循环与组装各取所需，默认值只此一处，避免两处各写一遍后慢慢漂开。
 */
final class ProfileSettings {

  static final int DEFAULT_MAX_ITERATIONS = 10;
  static final int DEFAULT_MAX_HISTORY_TURNS = 20;

  private static final String MAX_ITERATIONS = "max_iterations";
  private static final String MAX_HISTORY_TURNS = "max_history_turns";

  private ProfileSettings() {}

  static int maxIterations(Profile profile) {
    return positiveInt(profile, MAX_ITERATIONS, DEFAULT_MAX_ITERATIONS);
  }

  static int maxHistoryTurns(Profile profile) {
    return positiveInt(profile, MAX_HISTORY_TURNS, DEFAULT_MAX_HISTORY_TURNS);
  }

  /** 缺失、非数字、非正数一律回落默认值：配置写错不该让循环不设上限。 */
  private static int positiveInt(Profile profile, String key, int fallback) {
    Object raw = profile.settings().get(key);
    if (raw instanceof Number number && number.intValue() > 0) {
      return number.intValue();
    }
    return fallback;
  }
}
