# .idx/dev.nix
{ pkgs, ... }: {
  channel = "stable-23.11";
  packages = [
    pkgs.jdk21
    pkgs.maven
  ];
  idx = {
    extensions = [
      "vscjava.vscode-java-pack"
      "vmware.vscode-spring-boot"
      "gabrielbb.vscode-lombok"
    ];
    workspace = {
      onCreate = {
        mvn-install = "mvn clean install -DskipTests";
      };
    };
  };
}