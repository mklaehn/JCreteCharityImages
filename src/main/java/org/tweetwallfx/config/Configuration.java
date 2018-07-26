/*
 * The MIT License
 *
 * Copyright 2014-2018 TweetWallFX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.tweetwallfx.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tweetwall.util.JsonDataConverter;
import static org.tweetwall.util.ToString.*;

/**
 * Configuration store of data enabling influence into the configuration of the
 * Application.
 */
public final class Configuration {

    private static final String STANDARD_CONFIG_FILENAME = "tweetwallConfig.json";
    private static final String CUSTOM_CONFIG_FILENAME = System.getProperty("org.tweetwall.config.fileName");
    private static final Logger LOGGER = LogManager.getLogger(Configuration.class);
    private static final Map<String, ConfigurationConverter> CONVERTERS = StreamSupport
            .stream(ServiceLoader.load(ConfigurationConverter.class).spliterator(), false)
            .collect(Collectors.toMap(
                    ConfigurationConverter::getResponsibleKey,
                    Function.identity(),
                    (first, second) -> {
                        // ensure there are no conflicting ConfigurationConverter registered for a specific key
                        throw new IllegalArgumentException(String.format("At most one ConfigurationConverter may be registered to "
                                + "convert configuration data of a specific key, but the following ConfigurationConverters are "
                                + "registered for key '%s': [%s, %s]", first.getResponsibleKey(), first, second));
                    }));
    private static final Configuration INSTANCE = new Configuration();
    private final Map<String, Object> configurationData = new HashMap<>();

    private Configuration() {
        updateConfigurationData();
    }

    private void updateConfigurationData() {
        final Map<String, Object> configData = loadConfigurationData();

        LOGGER.info("Configurations:");
        configData.entrySet()
                .stream()
                .forEach(e -> LOGGER.info("'{}' -> '{}", e.getKey(), e.getValue()));

        configurationData.clear();
        configurationData.putAll(configData);
    }

    private static Map<String, Object> loadConfigurationData() {
        LOGGER.info("loading configurations data");
        Map<String, Object> result = Collections.emptyMap();

        for (Map<String, Object> map : Stream.concat(
                loadConfigurationDataFromClasspath(STANDARD_CONFIG_FILENAME),
                loadConfigurationDataFromFilesystem(STANDARD_CONFIG_FILENAME)).collect(Collectors.toList())) {
            result = mergeMap(result, map);
        }

        if (null != CUSTOM_CONFIG_FILENAME && !STANDARD_CONFIG_FILENAME.equals(CUSTOM_CONFIG_FILENAME)) {
            for (Map<String, Object> map : Stream.concat(
                    loadConfigurationDataFromClasspath(CUSTOM_CONFIG_FILENAME),
                    loadConfigurationDataFromFilesystem(CUSTOM_CONFIG_FILENAME)).collect(Collectors.toList())) {
                result = mergeMap(result, map);
            }
        }

        return convertConfigData(result);
    }

    private static Stream<Map<String, Object>> loadConfigurationDataFromClasspath(final String configFileName) {
        LOGGER.info("Searching for configuration files in path '/{}'", configFileName);
        Stream<Map<String, Object>> result = Stream.empty();

        try {
            final Enumeration<URL> resources = Configuration.class.getClassLoader().getResources(configFileName);

            while (resources.hasMoreElements()) {
                final URL url = resources.nextElement();
                LOGGER.info("Found config file: " + url);

                try (final InputStream is = url.openStream()) {
                    result = Stream.concat(result, Stream.of(readConfiguration(is, "Classpath entry '" + url.toExternalForm() + '\'')));
                }
            }
        } catch (final IOException ioe) {
            throw new RuntimeException("Error loading configuration data from classpath '/" + configFileName + "'", ioe);
        }

        return result;
    }

    private static Stream<Path> getConfigFilePaths(final String configFileName) {
        return Stream.of(
                Paths.get(System.getProperty("user.home"), configFileName),
                Paths.get("etc", configFileName),
                Paths.get(configFileName)
        );
    }

    private static Stream<Map<String, Object>> loadConfigurationDataFromFilesystem(final String configFileName) {
        return getConfigFilePaths(configFileName)
                .peek(p -> LOGGER.debug("Searching for configuration files at path: {}", p.toAbsolutePath()))
                .filter(Files::isRegularFile)
                .peek(p -> LOGGER.info("Found config override file: {}", p.toAbsolutePath()))
                .map(p -> {
                    try (InputStream is = Files.newInputStream(p)) {
                        return readConfiguration(is, "File '" + p.toString() + "'");
                    } catch (IOException ioe) {
                        throw new RuntimeException("Error loading configuration data from " + p, ioe);
                    }
                });
    }

    private static Map<String, Object> readConfiguration(final InputStream input, final String dataSourceIdentification) {
        try {
            final Map<String, Object> result = cast(JsonDataConverter.convertFromInputStream(input, Map.class));

            convertConfigData(result);

            return result;
        } catch (final Throwable t) {
            throw new RuntimeException(dataSourceIdentification + " either does not contain a valid JSONObject or has an invalid structure!", t);
        }
    }

    private static Map<String, Object> convertConfigData(final Map<String, Object> input) {
        // convert configuration data
        final Map<String, Object> result = new HashMap<>(input);

        CONVERTERS.entrySet()
                .stream()
                .peek(ce -> LOGGER.info("Processing key '{}' with ConfigurationConverter '{}'", ce.getKey(), ce.getValue()))
                .filter(e -> input.containsKey(e.getKey()))
                .forEach(e -> result.put(e.getKey(), JsonDataConverter.convertFromObject(input.get(e.getKey()), e.getValue().getDataClass())));

        return result;
    }

    public static Configuration getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the Object stored under the {@code param} in the configuration
     * data map. An entry must exist for the provided {@code param} otherwise a
     * {@link NullPointerException} is thrown.
     *
     * @param param the name of the requested entry
     *
     * @return the value of the requested entry
     *
     * @throws NullPointerException in case the configuration data map does not
     * contain a value for the requested {@code param}
     */
    public Object getConfig(final String param) {
        return Objects.requireNonNull(
                configurationData.get(param),
                "Configuration for '" + param + "' does not exist");
    }

    /**
     * Returns the Object stored under the {@code param} in the configuration
     * data map. If no value exists for the {@code param} the provided
     * {@code defaultValue} is returned.
     *
     * @param param the name of the requested entry
     *
     * @param defaultValue the value to return should no entry exist for the
     * {@code param}
     *
     * @return the value of the requested entry
     */
    public Object getConfig(final String param, final Object defaultValue) {
        return configurationData.getOrDefault(param, defaultValue);
    }

    /**
     * Returns the typesafe Object stored under the {@code param} in the
     * configuration data map. An entry must exist for the provided
     * {@code param} otherwise a {@link NullPointerException} is thrown.
     *
     * @param <T> the type of the requested value
     *
     * @param param the name of the requested entry
     *
     * @param paramClass the type class of the requested value
     *
     * @return the value of the requested entry
     *
     * @throws NullPointerException in case the configuration data map does not
     * contain a value for the requested {@code param}
     */
    public <T> T getConfigTyped(final String param, final Class<T> paramClass) {
        return cast(getConfig(param));
    }

    /**
     * Returns the typesafe Object stored under the {@code param} in the
     * configuration data map. If no value exists for the {@code param} the
     * provided {@code defaultValue} is returned.
     *
     * @param <T> the type of the requested value
     *
     * @param param the name of the requested entry
     *
     * @param paramClass the type class of the requested value
     *
     * @param defaultValue the value to return should no entry exist for the
     * {@code param}
     *
     * @return the value of the requested entry
     */
    public <T> T getConfigTyped(final String param, final Class<T> paramClass, final T defaultValue) {
        return cast(getConfig(param, defaultValue));
    }

    /**
     * Returns an {@link Optional} object containing the value stored under the
     * provided {@code param} key. Should no entry exist for the {@code param}
     * en empty {@link Optional} is returned.
     *
     * @param param the name of the requested entry
     *
     * @return Optional containg the value of an existing entry for the
     * {@code param} or an empty Optional
     */
    public Optional<Object> getConfigOptional(final String param) {
        return Optional.ofNullable(getConfig(param, null));
    }

    /**
     * Returns an {@link Optional} object containing the typesafe value stored
     * under the provided {@code param} key. Should no entry exist for the
     * {@code param} en empty {@link Optional} is returned.
     *
     * @param <T> the type of the requested value
     *
     * @param param the name of the requested entry
     *
     * @param paramClass the type class of the requested value
     *
     * @return Optional containg the value of an existing entry for the
     * {@code param} or an empty Optional
     */
    public <T> Optional<T> getConfigTypedOptional(final String param, final Class<T> paramClass) {
        return Optional.ofNullable(getConfigTyped(param, paramClass, null));
    }

    @Override
    public String toString() {
        return createToString(this, map(
                "configurationData", configurationData
        ));
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(final Object obj) {
        return (T) obj;
    }

    private static Map<String, Object> mergeMap(final Map<String, Object> previous, final Map<String, Object> next) {
        Objects.requireNonNull(next, "Parameter next must not be null!");

        if (null == previous || previous.isEmpty()) {
            return next;
        }

        return Stream.concat(previous.keySet().stream(), next.keySet().stream())
                .sorted()
                .distinct()
                .collect(Collectors.toMap(Function.identity(), key -> mergeValue(key, previous.get(key), next.get(key))));
    }

    private static Object mergeValue(final String key, final Object previous, final Object next) {
        if (null == previous) {
            return Objects.requireNonNull(next, key + " Parameter next must not be null!");
        } else if (null == next) {
            return Objects.requireNonNull(previous, key + " Parameter previous must not be null!");
        }

        final Class<?> pClass = previous.getClass();
        final Class<?> nClass = next.getClass();

        if (pClass.getName().startsWith("java.lang.") || nClass.getName().startsWith("java.lang.")) {
            return next;
        } else if (Map.class.isInstance(previous)
                && Map.class.isInstance(next)) {
            return mergeMap(cast(previous), cast(next));
        } else {
            throw new UnsupportedOperationException("Merging type " + pClass + " with " + nClass + " is not supported!");
        }
    }
}
