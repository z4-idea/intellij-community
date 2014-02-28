/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.List;

/**
 * Created by Max Medvedev on 27/02/14
 */
public class ClosureParamsEnhancer extends AbstractClosureParameterEnhancer {

  @Nullable
  @Override
  protected PsiType getClosureParameterType(GrClosableBlock closure, int index) {
    if (!GroovyConfigUtils.getInstance().isVersionAtLeast(closure, GroovyConfigUtils.GROOVY2_3)) return null;

    GrParameter[] parameters = closure.getAllParameters();
    if (parameters.length != 1) return null;

    GrParameter parameter = parameters[index];
    if (parameter.getDeclaredType() != null) return null;

    GrCall call = findCall(closure);
    if (call == null) return null;

    GroovyResolveResult resolveResult = call.advancedResolve();
    PsiElement element = resolveResult.getElement();

    if (!(element instanceof PsiMethod)) {
      return null;
    }

    List<Pair<PsiParameter,PsiType>> params = ResolveUtil.collectExpectedParamsByArg(closure, new GroovyResolveResult[]{resolveResult}, call.getNamedArguments(), call.getExpressionArguments(), call.getClosureArguments(), closure);
    if (params.isEmpty()) return null;

    Pair<PsiParameter, PsiType> pair = params.get(0);

    PsiParameter param = pair.getFirst();
    PsiModifierList modifierList = param.getModifierList();
    if (modifierList == null) return null;

    PsiAnnotation anno = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_STC_CLOSURE_PARAMS);
    if (anno == null) return null;

    PsiClass closureSignatureHint = GrAnnotationUtil.inferClassAttribute(anno, "value");
    if (closureSignatureHint == null) return null;

    String qnameOfClosureSignatureHint = closureSignatureHint.getQualifiedName();

    SignatureHintProcessor signatureHintProcessor = SignatureHintProcessor.getHintProcessor(qnameOfClosureSignatureHint);
    if (signatureHintProcessor == null) return null;

    List<PsiType[]> expectedSignatures = signatureHintProcessor.inferExpectedSignatures((PsiMethod)element,
                                                                                         resolveResult.getSubstitutor(),
                                                                                         anno.findAttributeValue("options"));
    if (expectedSignatures.size() == 1) {
      PsiType[] expectedSignature = expectedSignatures.get(0);
      if (expectedSignature.length == 1) {
        return expectedSignature[0];
      }
    }

    return null;
  }

  @Nullable
  private static GrCall findCall(@NotNull GrClosableBlock closure) {
    PsiElement parent = closure.getParent();
    if (parent instanceof GrCall && ArrayUtil.contains(closure, ((GrCall)parent).getClosureArguments())) {
      return (GrCall)parent;
    }

    if (parent instanceof GrArgumentList) {
      PsiElement pparent = parent.getParent();
      if (pparent instanceof GrCall) {
        return (GrCall)pparent;
      }
    }

    return null;
  }
}
