package com.example.mobresistlock;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Talks to Tensura's skill storage (ManasCore) through reflection, so this mod compiles
 * without Tensura on the classpath. The method names below are best guesses for ManasCore
 * on 1.21.1; if any of them do not exist, /edit resistances debug tells you which one failed.
 */
final class TensuraBridge {
    private static final String SKILL_API = "io.github.manasmods.manascore.skill.api.SkillAPI";

    private static boolean tried;
    private static Method getSkillsFrom;
    private static Method getLearned;
    private static Method forget;
    private static Method getSkill;
    private static String report = "not resolved yet";

    private TensuraBridge() {}

    // ---------- public helpers ----------

    static synchronized String statusReport() {
        resolve();
        return report;
    }

    /** Resistance descriptors currently on the mob, or null if the bridge is unavailable. */
    static Set<String> currentResistances(LivingEntity entity) {
        List<Object> instances = learnedInstances(entity);
        if (instances == null) {
            return null;
        }
        Set<String> out = new HashSet<>();
        for (Object inst : instances) {
            String d = describe(inst);
            if (looksLikeResistance(d)) {
                out.add(d);
            }
        }
        return out;
    }

    /** Removes resistances that are not part of the baseline. Returns how many were removed. */
    static int removeResistancesNotIn(LivingEntity entity, Set<String> baseline) {
        List<Object> instances = learnedInstances(entity);
        if (instances == null || forget == null) {
            return 0;
        }
        Object storage = storageOf(entity);
        int removed = 0;
        for (Object inst : instances) {
            String d = describe(inst);
            if (!looksLikeResistance(d) || baseline.contains(d)) {
                continue;
            }
            if (forgetOne(storage, inst)) {
                removed++;
            }
        }
        return removed;
    }

    static List<String> describeAllSkills(LivingEntity entity) {
        List<Object> instances = learnedInstances(entity);
        if (instances == null) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (Object inst : instances) {
            out.add(describe(inst));
        }
        return out;
    }

    static boolean looksLikeResistance(String descriptor) {
        String s = descriptor.toLowerCase();
        return s.contains("resist") || s.contains("nullif");
    }

    // ---------- reflection internals ----------

    private static synchronized void resolve() {
        if (tried) {
            return;
        }
        tried = true;
        StringBuilder sb = new StringBuilder();
        try {
            Class<?> api = Class.forName(SKILL_API);
            sb.append("SkillAPI found. ");
            getSkillsFrom = find(api, 1, true, "getSkillsFrom", "getSkillsOf", "getSkills");
            sb.append("getSkillsFrom=").append(getSkillsFrom != null).append(". ");
            if (getSkillsFrom != null) {
                Class<?> storageType = getSkillsFrom.getReturnType();
                if (storageType == Optional.class) {
                    sb.append("(returns Optional, storage class unknown until first use) ");
                } else {
                    resolveStorageMethods(storageType, sb);
                }
            }
        } catch (ClassNotFoundException e) {
            sb.append("ManasCore SkillAPI class not found (is Tensura installed?).");
        } catch (Throwable t) {
            sb.append("Resolve failed: ").append(t);
        }
        report = sb.toString();
    }

    private static void resolveStorageMethods(Class<?> storageType, StringBuilder sb) {
        getLearned = find(storageType, 0, false, "getLearnedSkills", "getAllSkills", "getSkills");
        forget = find(storageType, 1, false, "forgetSkill", "removeSkill");
        sb.append("getLearnedSkills=").append(getLearned != null)
          .append(", forgetSkill=").append(forget != null).append(". ");
    }

    private static Object storageOf(LivingEntity entity) {
        resolve();
        if (getSkillsFrom == null) {
            return null;
        }
        try {
            Object result = getSkillsFrom.invoke(null, entity);
            if (result instanceof Optional<?> opt) {
                result = opt.orElse(null);
            }
            if (result != null && getLearned == null) {
                resolveStorageMethods(result.getClass(), new StringBuilder());
            }
            return result;
        } catch (Throwable t) {
            MobResistLock.LOGGER.debug("getSkillsFrom failed", t);
            return null;
        }
    }

    private static List<Object> learnedInstances(LivingEntity entity) {
        Object storage = storageOf(entity);
        if (storage == null || getLearned == null) {
            return null;
        }
        try {
            Object result = getLearned.invoke(storage);
            Collection<?> col;
            if (result instanceof Collection<?> c) {
                col = c;
            } else if (result instanceof Map<?, ?> m) {
                col = m.values();
            } else {
                return null;
            }
            return new ArrayList<>(col);
        } catch (Throwable t) {
            MobResistLock.LOGGER.debug("getLearnedSkills failed", t);
            return null;
        }
    }

    private static boolean forgetOne(Object storage, Object instance) {
        if (storage == null || forget == null) {
            return false;
        }
        Object skill = skillOf(instance);
        Class<?> param = forget.getParameterTypes()[0];
        Object arg = param.isInstance(instance) ? instance : (skill != null && param.isInstance(skill) ? skill : null);
        if (arg == null) {
            return false;
        }
        try {
            forget.invoke(storage, arg);
            return true;
        } catch (Throwable t) {
            MobResistLock.LOGGER.debug("forgetSkill failed", t);
            return false;
        }
    }

    private static Object skillOf(Object instance) {
        try {
            if (getSkill == null) {
                getSkill = find(instance.getClass(), 0, false, "getSkill");
            }
            return getSkill != null ? getSkill.invoke(instance) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** A string for an instance: registry id if we can get one, plus the class name. */
    private static String describe(Object instance) {
        Object skill = skillOf(instance);
        Object target = skill != null ? skill : instance;
        Object id = null;
        for (String name : new String[] {"getRegistryName", "getId", "getResourceLocation"}) {
            Method m = find(target.getClass(), 0, false, name);
            if (m != null) {
                try {
                    id = m.invoke(target);
                } catch (Throwable ignored) {
                    // try next
                }
                if (id != null) {
                    break;
                }
            }
        }
        String cls = target.getClass().getName();
        return id != null ? id + " (" + cls + ")" : cls;
    }

    private static Method find(Class<?> type, int paramCount, boolean wantStatic, String... names) {
        for (String name : names) {
            for (Method m : type.getMethods()) {
                if (m.getName().equals(name)
                        && m.getParameterCount() == paramCount
                        && Modifier.isStatic(m.getModifiers()) == wantStatic) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }
}
