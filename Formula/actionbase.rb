class Actionbase < Formula
  desc "actionbase CLI"
  homepage "https://github.com/kakao/actionbase"
  version "0.0.1"
  license "MIT"

  ENV["CGO_ENABLED"] = "0"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/kakao/actionbase/releases/download/cli/v#{version}/actionbase_darwin_arm64.tar.gz"
      sha256 "4697025bf425ffea9ee5b783babbc64116d90d4d5bf9a533b68739a344170c87"
    else
      url "https://github.com/kakao/actionbase/releases/download/cli/v#{version}/actionbase_darwin_amd64.tar.gz"
      sha256 "3cd095b62faac82afbd4f806fd1d54e8a005b960864ef34f598d936ec2afaac0"
    end
  end

  on_linux do
    url "https://github.com/kakao/actionbase/releases/download/cli/v#{version}/actionbase_linux_amd64.tar.gz"
    sha256 "d959c5a48a78f0b7babdfea0a3bb5876111bbb0fa3271676a19e2ff26ec86a9d"
  end

  def install
    bin.install "actionbase"
  end

  test do
    system "#{bin}/actionbase", "--version"
  end
end
