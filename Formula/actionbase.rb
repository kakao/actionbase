class Actionbase < Formula
  desc "actionbase CLI"
  homepage "https://github.com/kakao/actionbase"
  version "0.0.1"
  license "Apache-2.0 license"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/kakao/actionbase/releases/download/cli/v#{version}/actionbase_darwin_arm64.tar.gz"
      sha256 ""
    else
      url "https://github.com/kakao/actionbase/releases/download/cli/v#{version}/actionbase_darwin_amd64.tar.gz"
      sha256 ""
    end
  end

  on_linux do
    url "https://github.com/kakao/actionbase/releases/download/cli/v#{version}/actionbase_linux_amd64.tar.gz"
    sha256 ""
  end

  def install
    bin.install "actionbase"
  end

  test do
    system "#{bin}/actionbase", "--version"
  end
end
