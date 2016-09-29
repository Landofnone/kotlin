/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.move.moveInner.MoveInnerClassUsagesHandler
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.refactoring.changeSignature.usages.explicateReceiverOf
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver

class MoveJavaInnerClassKotlinUsagesHandler: MoveInnerClassUsagesHandler {
    override fun correctInnerClassUsage(usage: UsageInfo, outerClass: PsiClass) {
        val innerCall = usage.element?.parent as? KtCallExpression ?: return

        val resolvedCall = innerCall.getResolvedCall(innerCall.analyze(BodyResolveMode.PARTIAL)) ?: return
        val receiverValue = resolvedCall.dispatchReceiver ?: return

        val psiFactory = KtPsiFactory(usage.project)

        val argumentExpressionToAdd = when (receiverValue) {
            is ExpressionReceiver -> receiverValue.expression
            is ImplicitReceiver -> psiFactory.createExpression(explicateReceiverOf(receiverValue.declarationDescriptor))
            else -> null
        } ?: return
        val argumentToAdd = psiFactory.createArgument(argumentExpressionToAdd)

        val needToShortenThis = receiverValue is ImplicitReceiver

        val argumentList =
                innerCall.valueArgumentList
                ?: (innerCall.lambdaArguments.firstOrNull()?.let { lambdaArg ->
                    val anchor = PsiTreeUtil.skipSiblingsBackward(lambdaArg, PsiWhiteSpace::class.java)
                    innerCall.addAfter(psiFactory.createCallArguments("()"), anchor)
                } as KtValueArgumentList?)
                ?: return

        val newArgument = argumentList.addArgumentAfter(argumentToAdd, null)
        if (needToShortenThis) {
            ShortenReferences { ShortenReferences.Options(removeThisLabels = true) }.process(newArgument)
        }
    }
}