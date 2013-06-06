package com.jetbrains.python.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class PythonSpaceHandler extends TypedHandlerDelegate {
  @Override
  public Result charTyped(char c, Project project, Editor editor, @NotNull PsiFile file) {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    if (c == ' ' && codeInsightSettings.JAVADOC_STUB_ON_ENTER) {
      int offset = editor.getCaretModel().getOffset();
      PsiElement element = file.findElementAt(offset);
      if (element == null && offset > 1)
        element = file.findElementAt(offset-2);
      if (element == null) return Result.CONTINUE;
      int expectedStringStart = editor.getCaretModel().getOffset()-4;        // """ or ''' plus space char
      if (PythonDocCommentUtil.atDocCommentStart(element, expectedStringStart)) {
        PythonDocumentationProvider provider = new PythonDocumentationProvider();
        PyFunction fun = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        final PsiElement parent = element.getParent();
        if (fun != null) {
          String docStub = provider.generateDocumentationContentStub(fun, false);
          docStub += parent.getText().substring(0,3);
          if (docStub.length() != 0) {
            editor.getDocument().insertString(editor.getCaretModel().getOffset(), docStub);
            Module module = ModuleUtilCore.findModuleForPsiElement(element);
            if (module == null) return Result.CONTINUE;
            PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(module);
            if (documentationSettings.myDocStringFormat != DocStringFormat.PLAIN)
              editor.getCaretModel().moveCaretRelatively(100, 1, false, false, false);
            return Result.STOP;
          }
        }
        PyElement klass = PsiTreeUtil.getParentOfType(element, PyClass.class, PyFile.class);
        if (klass != null) {
          editor.getDocument().insertString(editor.getCaretModel().getOffset(),
                          PythonDocCommentUtil.generateDocForClass(klass, parent.getText().substring(0, 3)));
          return Result.STOP;
        }
      }
    }
    return Result.CONTINUE;
  }
}
