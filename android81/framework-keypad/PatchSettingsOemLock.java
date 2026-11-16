import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.builder.Label;
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.builder.instruction.BuilderInstruction10t;
import org.jf.dexlib2.builder.instruction.BuilderInstruction11n;
import org.jf.dexlib2.builder.instruction.BuilderInstruction11x;
import org.jf.dexlib2.builder.instruction.BuilderInstruction21t;
import org.jf.dexlib2.builder.instruction.BuilderInstruction35c;
import org.jf.dexlib2.builder.instruction.BuilderInstruction31c;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;

public final class PatchSettingsOemLock {
    private static final String DEVELOPMENT =
            "Lcom/android/settings/development/DevelopmentSettings;";
    private static final ImmutableMethodReference CHECK_SERVICE = new ImmutableMethodReference(
            "Landroid/os/ServiceManager;", "checkService",
            Arrays.asList("Ljava/lang/String;"), "Landroid/os/IBinder;");

    public static void main(String[] args) throws Exception {
        Opcodes api27 = Opcodes.forApi(27);
        List<ClassDef> classes = new ArrayList<>();
        int patched = 0;
        for (ClassDef cls : DexFileFactory.loadDexFile(new File(args[0]), api27).getClasses()) {
            if (!DEVELOPMENT.equals(cls.getType())) {
                classes.add(cls);
                continue;
            }
            List<Method> methods = new ArrayList<>();
            for (Method method : cls.getMethods()) {
                String name = method.getName();
                if (name.equals("showEnableOemUnlockPreference") || name.equals("onCreate")) {
                    methods.add(patch(method));
                    patched++;
                } else {
                    methods.add(method);
                }
            }
            classes.add(new ImmutableClassDef(cls.getType(), cls.getAccessFlags(),
                    cls.getSuperclass(), cls.getInterfaces(), cls.getSourceFile(),
                    cls.getAnnotations(), cls.getFields(), methods));
        }
        if (patched != 2) {
            throw new IllegalStateException("Expected both OEM lock lookups");
        }
        DexFileFactory.writeDexFile(args[1], new ImmutableDexFile(api27, classes));
    }

    private static Method patch(Method method) {
        MutableMethodImplementation body = new MutableMethodImplementation(method.getImplementation());
        List<? extends Instruction> instructions = body.getInstructions();
        int lookup = -1;
        int register = -1;
        for (int i = 0; i + 2 < instructions.size(); i++) {
            Instruction key = instructions.get(i);
            if ((key.getOpcode() == Opcode.CONST_STRING || key.getOpcode() == Opcode.CONST_STRING_JUMBO)
                    && ((StringReference) ((ReferenceInstruction) key).getReference())
                            .getString().equals("oem_lock")) {
                if (lookup >= 0) {
                    throw new IllegalStateException("Multiple OEM lock references in " + method);
                }
                lookup = i + 1;
                register = ((OneRegisterInstruction) key).getRegisterA();
            }
        }
        if (lookup < 0 || instructions.get(lookup).getOpcode() != Opcode.INVOKE_VIRTUAL
                || !((MethodReference) ((ReferenceInstruction) instructions.get(lookup))
                        .getReference()).getName().equals("getSystemService")
                || instructions.get(lookup + 1).getOpcode() != Opcode.MOVE_RESULT_OBJECT
                || ((OneRegisterInstruction) instructions.get(lookup + 1)).getRegisterA() != register) {
            throw new IllegalStateException("Unexpected OEM lock lookup in " + method);
        }
        BuilderInstruction35c check = new BuilderInstruction35c(Opcode.INVOKE_STATIC, 1,
                register, 0, 0, 0, 0, CHECK_SERVICE);
        if (method.getName().equals("showEnableOemUnlockPreference")) {
            body.replaceInstruction(lookup, check);
        } else {
            if (instructions.get(lookup + 2).getOpcode() != Opcode.CHECK_CAST
                    || instructions.get(lookup + 3).getOpcode() != Opcode.IPUT_OBJECT) {
                throw new IllegalStateException("Unexpected OEM manager assignment");
            }
            Label getManager = body.newLabelForIndex(lookup - 1);
            Label assign = body.newLabelForIndex(lookup + 3);
            int guard = lookup - 1;
            body.addInstruction(guard++, new BuilderInstruction31c(Opcode.CONST_STRING_JUMBO,
                    register, new ImmutableStringReference("oem_lock")));
            body.addInstruction(guard++, check);
            body.addInstruction(guard++, new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, register));
            body.addInstruction(guard++, new BuilderInstruction21t(Opcode.IF_NEZ, register, getManager));
            body.addInstruction(guard++, new BuilderInstruction11n(Opcode.CONST_4, register, 0));
            body.addInstruction(guard, new BuilderInstruction10t(Opcode.GOTO, assign));
        }
        return new ImmutableMethod(method.getDefiningClass(), method.getName(), method.getParameters(),
                method.getReturnType(), method.getAccessFlags(), method.getAnnotations(),
                method.getHiddenApiRestrictions(), body);
    }
}
