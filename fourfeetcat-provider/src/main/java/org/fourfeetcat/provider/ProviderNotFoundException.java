package org.fourfeetcat.provider;

/** Profile 引用了显式映射表里不存在的 provider 名：直接报错，不悄悄用错、不留空跑过去。 */
public class ProviderNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ProviderNotFoundException(String providerName) {
    super("未找到名为 '" + providerName + "' 的 provider，请在全局配置 fourfeetcat.providers 里声明");
  }
}
