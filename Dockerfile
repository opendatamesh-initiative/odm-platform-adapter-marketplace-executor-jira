# Runtime image for a native executable built on the host or in CI, e.g.:
#   ./mvnw package -DskipTests -Dnative -Dquarkus.native.container-build=true
FROM registry.access.redhat.com/ubi9/ubi-minimal:9.8
WORKDIR /work/
RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work
COPY --chown=1001:root --chmod=0755 target/*-runner /work/application

EXPOSE 9019
USER 1001

ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
