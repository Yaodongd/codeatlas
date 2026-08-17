package dev.codeatlas.project;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

@Component
public class RepositoryUrlValidator {
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "github.com", "gitlab.com", "gitee.com", "codeberg.org"
    );

    public URI validate(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("仓库地址必须使用 HTTPS");
            }
            if (uri.getUserInfo() != null || uri.getPort() != -1 || uri.getFragment() != null) {
                throw new IllegalArgumentException("仓库地址不能包含账号、端口或片段");
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (!ALLOWED_HOSTS.contains(host)) {
                throw new IllegalArgumentException("第一版仅支持 GitHub、GitLab、Gitee 和 Codeberg 公共仓库");
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("仓库域名解析到了非公网地址");
                }
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("仓库地址无法验证", exception);
        }
    }
}

