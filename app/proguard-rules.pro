

# StackFactory - regras complementares do R8 (Release).
# Nao usar -dontobfuscate ou -dontshrink; desativariam a otimizacao.
# Firebase, Compose e AdMob incluem consumer rules no proprio AAR.

# WorkManager resolve Worker pelo nome da classe gravado em sua base.
# Preserva identidade e construtor de cada Worker, sem impedir a
# ofuscacao do restante do codigo do jogo.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Preserva informacoes necessarias para algumas bibliotecas reflexivas.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Nao incluir regras globais de keep para com.diegoluan.stackfactory.**.
