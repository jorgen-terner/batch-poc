package infrastruktur.batch.contract;

import java.lang.reflect.Constructor;
import java.util.ServiceLoader;

final class ServiceProviderSupport {
    private ServiceProviderSupport() {
    }

    static <T> T resolveProvider(
            Class<T> serviceType,
            String implementationClassName,
            ClassLoader providerClassLoader) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        T provider = loadFirstFromClassLoader(serviceType, contextClassLoader);
        if (provider != null) {
            return provider;
        }

        if (providerClassLoader != contextClassLoader) {
            provider = loadFirstFromClassLoader(serviceType, providerClassLoader);
            if (provider != null) {
                return provider;
            }
        }

        provider = instantiateFromClassLoader(serviceType, implementationClassName, contextClassLoader);
        if (provider != null) {
            return provider;
        }

        if (providerClassLoader != contextClassLoader) {
            return instantiateFromClassLoader(serviceType, implementationClassName, providerClassLoader);
        }
        return null;
    }

    private static <T> T loadFirstFromClassLoader(Class<T> serviceType, ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        for (T provider : ServiceLoader.load(serviceType, classLoader)) {
            return provider;
        }
        return null;
    }

    private static <T> T instantiateFromClassLoader(
            Class<T> serviceType,
            String implementationClassName,
            ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        try {
            Class<?> candidate = Class.forName(implementationClassName, true, classLoader);
            if (!serviceType.isAssignableFrom(candidate)) {
                return null;
            }
            Constructor<?> constructor = candidate.getDeclaredConstructor();
            constructor.setAccessible(true);
            return serviceType.cast(constructor.newInstance());
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
