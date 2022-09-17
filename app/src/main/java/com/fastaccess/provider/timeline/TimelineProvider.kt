package com.fastaccess.provider.timeline

import android.content.Context
import android.graphics.Color
import android.text.style.BackgroundColorSpan
import com.fastaccess.R
import com.fastaccess.data.dao.timeline.GenericEvent
import com.fastaccess.data.dao.types.IssueEventType
import com.fastaccess.data.entity.User
import com.fastaccess.helper.InputHelper.isEmpty
import com.fastaccess.helper.ParseDateFormat.Companion.getTimeAgo
import com.fastaccess.helper.PrefGetter.themeType
import com.fastaccess.helper.ViewHelper
import com.fastaccess.provider.timeline.HtmlHelper.getWindowBackground
import com.fastaccess.ui.widgets.SpannableBuilder
import com.fastaccess.ui.widgets.SpannableBuilder.Companion.builder
import com.zzhoujay.markdown.style.CodeSpan
import java.util.*

/**
 * Created by Kosh on 20 Apr 2017, 7:18 PM
 */
object TimelineProvider {
    @JvmStatic
    fun getStyledEvents(
        issueEventModel: GenericEvent,
        context: Context, isMerged: Boolean
    ): SpannableBuilder {
        val event = issueEventModel.event
        val spannableBuilder = builder()
        val date =
            if (issueEventModel.createdAt != null) issueEventModel.createdAt else if (issueEventModel.author != null) issueEventModel.author!!.date else null
        if (event != null) {

            val eventName = event.name.replaceAndLowercase()

            val to = context.getString(R.string.to)
            val from = context.getString(R.string.from)
            val thisString = context.getString(R.string.this_value)
            val `in` = context.getString(R.string.in_value)
            if (event === IssueEventType.LABELED || event === IssueEventType.UNLABELED) {
                spannableBuilder.bold(if (issueEventModel.actor != null) issueEventModel.actor!!.login!! else "anonymous")
                spannableBuilder.append(" ").append(eventName)
                val labelModel = issueEventModel.label!!
                val color = Color.parseColor("#" + labelModel.color)
                spannableBuilder.append(" ").append(
                    " " + labelModel.name + " ",
                    CodeSpan(color, ViewHelper.generateTextColor(color), 5F)
                )
                spannableBuilder.append(" ").append(getDate(issueEventModel.createdAt))
            } else if (event === IssueEventType.COMMITTED) {
                spannableBuilder.append(issueEventModel.message!!.replace("\n".toRegex(), " "))
                    .append(" ")
                    .url(substring(issueEventModel.sha))
            } else {
                var user: User? = null
                if (issueEventModel.assignee != null && issueEventModel.assigner != null) {
                    user = issueEventModel.assigner
                } else if (issueEventModel.actor != null) {
                    user = issueEventModel.actor
                } else if (issueEventModel.author != null) {
                    user = issueEventModel.author
                }
                if (user != null) {
                    spannableBuilder.bold(user.login!!)
                }
                if ((event === IssueEventType.REVIEW_REQUESTED || event === IssueEventType.REVIEW_DISMISSED ||
                            event === IssueEventType.REVIEW_REQUEST_REMOVED) && user != null
                ) {
                    appendReviews(
                        issueEventModel,
                        event,
                        spannableBuilder,
                        from,
                        issueEventModel.reviewRequester!!
                    )
                } else if (event === IssueEventType.CLOSED || event === IssueEventType.REOPENED) {
                    if (isMerged) {
                        spannableBuilder.append(" ").append(IssueEventType.MERGED.name)
                    } else {
                        spannableBuilder
                            .append(" ")
                            .append(eventName)
                            .append(" ")
                            .append(thisString)
                    }
                    if (issueEventModel.commitId != null) {
                        spannableBuilder
                            .append(" ")
                            .append(`in`)
                            .append(" ")
                            .url(substring(issueEventModel.commitId))
                    }
                } else if (event === IssueEventType.ASSIGNED || event === IssueEventType.UNASSIGNED) {
                    spannableBuilder
                        .append(" ")
                    if (user != null && issueEventModel.assignee != null && user.login
                            .equals(issueEventModel.assignee!!.login, ignoreCase = true)
                    ) {
                        spannableBuilder
                            .append(if (event === IssueEventType.ASSIGNED) "self-assigned this" else "removed their assignment")
                    } else {
                        spannableBuilder
                            .append(if (event === IssueEventType.ASSIGNED) "assigned" else "unassigned")
                        spannableBuilder
                            .append(" ")
                            .bold(if (issueEventModel.assignee != null) issueEventModel.assignee!!.login!! else "")
                    }
                } else if (event === IssueEventType.LOCKED || event === IssueEventType.UNLOCKED) {
                    spannableBuilder
                        .append(" ")
                        .append(
                            if (event === IssueEventType.LOCKED) "locked and limited conversation to collaborators" else "unlocked this " +
                                    "conversation"
                        )
                } else if (event === IssueEventType.HEAD_REF_DELETED || event === IssueEventType.HEAD_REF_RESTORED) {
                    spannableBuilder.append(" ").append(
                        eventName,
                        BackgroundColorSpan(getWindowBackground(themeType))
                    )
                } else if (event === IssueEventType.MILESTONED || event === IssueEventType.DEMILESTONED) {
                    spannableBuilder.append(" ")
                        .append(if (event === IssueEventType.MILESTONED) "added this to the" else "removed this from the")
                        .append(" ")
                        .bold(issueEventModel.milestone!!.title!!)
                        .append(" ")
                        .append("milestone")
                } else if (event === IssueEventType.DEPLOYED) {
                    spannableBuilder.append(" ")
                        .bold("deployed")
                } else {
                    spannableBuilder.append(" ").append(eventName)
                }
                if (event === IssueEventType.RENAMED) {
                    spannableBuilder
                        .append(" ")
                        .append(from)
                        .append(" ")
                        .bold(issueEventModel.rename!!.fromValue!!)
                        .append(" ")
                        .append(to)
                        .append(" ")
                        .bold(issueEventModel.rename!!.toValue!!)
                } else if (event === IssueEventType.REFERENCED || event === IssueEventType.MERGED) {
                    spannableBuilder
                        .append(" ")
                        .append("commit")
                        .append(" ")
                        .url(substring(issueEventModel.commitId))
                } else if (event === IssueEventType.CROSS_REFERENCED) {
                    val sourceModel = issueEventModel.source
                    if (sourceModel != null) {
                        var type = sourceModel.type
                        val title = builder()
                        when {
                            sourceModel.pullRequest != null -> {
                                if (sourceModel.issue != null) title.url("#" + sourceModel.issue!!.number)
                                type = "pull request"
                            }
                            sourceModel.issue != null -> {
                                title.url("#" + sourceModel.issue!!.number)
                            }
                            sourceModel.commit != null -> {
                                title.url(substring(sourceModel.commit!!.sha))
                            }
                            sourceModel.repository != null -> {
                                title.url(sourceModel.repository!!.name!!)
                            }
                        }
                        if (!isEmpty(title)) {
                            spannableBuilder.append(" ")
                                .append(thisString)
                                .append(" in ")
                                .append(type)
                                .append(" ")
                                .append(title)
                        }
                    }
                }
                spannableBuilder.append(" ").append(getDate(date))
            }
        }
        return spannableBuilder
    }

    private fun appendReviews(
        issueEventModel: GenericEvent, event: IssueEventType,
        spannableBuilder: SpannableBuilder, from: String,
        user: User
    ) {
        spannableBuilder.append(" ")
        val reviewer = issueEventModel.requestedReviewer
        if (reviewer != null && user.login.equals(reviewer.login, ignoreCase = true)) {
            spannableBuilder
                .append(if (event === IssueEventType.REVIEW_REQUESTED) "self-requested a review" else "removed their request for review")
        } else {
            spannableBuilder
                .append(if (event === IssueEventType.REVIEW_REQUESTED) "Requested a review" else "dismissed the review")
                .append(" ")
                .append(
                    if (reviewer != null && !reviewer.login.equals(
                            user.login,
                            ignoreCase = true
                        )
                    ) from else " "
                )
                .append(
                    if (reviewer != null && !reviewer.login.equals(
                            user.login,
                            ignoreCase = true
                        )
                    ) " " else ""
                )
        }
        if (issueEventModel.requestedTeam != null) {
            val name =
                if (!isEmpty(issueEventModel.requestedTeam!!.name)) issueEventModel.requestedTeam!!.name else issueEventModel.requestedTeam!!.slug
            spannableBuilder
                .bold(name!!)
                .append(" ")
                .append("team")
        } else if (reviewer != null && !user.login.equals(reviewer.login, ignoreCase = true)) {
            spannableBuilder.bold(issueEventModel.requestedReviewer!!.login!!)
        }
    }

    private fun getDate(date: Date?): CharSequence {
        return getTimeAgo(date)
    }

    private fun substring(value: String?): String {
        if (value == null) {
            return ""
        }
        return if (value.length <= 7) value else value.substring(0, 7)
    }

    private fun String.replaceAndLowercase() = replace("_".toRegex(), " ").lowercase()
}