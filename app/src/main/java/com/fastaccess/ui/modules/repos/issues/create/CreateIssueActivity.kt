package com.fastaccess.ui.modules.repos.issues.create

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.transition.TransitionManager
import com.evernote.android.state.State
import com.fastaccess.App
import com.fastaccess.BuildConfig
import com.fastaccess.R
import com.fastaccess.data.dao.LabelListModel
import com.fastaccess.data.dao.LabelModel
import com.fastaccess.data.dao.MilestoneModel
import com.fastaccess.data.entity.Issue
import com.fastaccess.data.entity.PullRequest
import com.fastaccess.data.entity.User
import com.fastaccess.helper.*
import com.fastaccess.helper.InputHelper.isEmpty
import com.fastaccess.helper.InputHelper.toString
import com.fastaccess.helper.Logger.e
import com.fastaccess.provider.markdown.MarkDownProvider.setMdText
import com.fastaccess.ui.base.BaseActivity
import com.fastaccess.ui.modules.editor.EditorActivity
import com.fastaccess.ui.modules.repos.extras.assignees.AssigneesDialogFragment
import com.fastaccess.ui.modules.repos.extras.labels.LabelsDialogFragment
import com.fastaccess.ui.modules.repos.extras.milestone.create.MilestoneDialogFragment
import com.fastaccess.ui.widgets.FontTextView
import com.fastaccess.ui.widgets.LabelSpan
import com.fastaccess.ui.widgets.SpannableBuilder.Companion.builder
import com.fastaccess.ui.widgets.dialog.MessageDialogView
import com.fastaccess.ui.widgets.dialog.MessageDialogView.Companion.newInstance
import com.fastaccess.utils.setOnThrottleClickListener
import com.google.android.material.textfield.TextInputLayout
import es.dmoral.toasty.Toasty

/**
 * Created by Kosh on 19 Feb 2017, 12:33 PM
 */
class CreateIssueActivity : BaseActivity<CreateIssueMvp.View, CreateIssuePresenter>(),
    CreateIssueMvp.View {
    val title: TextInputLayout? by lazy { viewFind(R.id.title) }
    val description: FontTextView? by lazy { viewFind(R.id.description) }
    val submit: View? by lazy { viewFind(R.id.submit) }
    private val issueMiscLayout: LinearLayout? by lazy { viewFind(R.id.issueMiscLayout) }
    val assignee: FontTextView? by lazy { viewFind(R.id.assignee) }
    val labels: FontTextView? by lazy { viewFind(R.id.labels) }
    val milestoneTitle: FontTextView? by lazy { viewFind(R.id.milestoneTitle) }
    val milestoneDescription: FontTextView? by lazy { viewFind(R.id.milestoneDescription) }

    @State
    var repoId: String? = null

    @State
    var login: String? = null

    @State
    var issue: Issue? = null

    @State
    var pullRequest: PullRequest? = null

    @State
    var isFeedback = false

    @State
    var labelModels = ArrayList<LabelModel>()

    @State
    var milestoneModel: MilestoneModel? = null

    @State
    var users = ArrayList<User>()
    private var alertDialog: AlertDialog? = null
    private var savedText: CharSequence = ""
    override fun onSetCode(charSequence: CharSequence) {
        savedText = charSequence
        setMdText(description!!, toString(savedText))
    }

    override fun onTitleError(isEmptyTitle: Boolean) {
        title!!.error = if (isEmptyTitle) getString(R.string.required_field) else null
    }

    override fun onDescriptionError(isEmptyDesc: Boolean) {
        description!!.error = if (isEmptyDesc) getString(R.string.required_field) else null
    }

    override fun onSuccessSubmission(issueModel: Issue) {
        hideProgress()
        val intent = Intent()
        intent.putExtras(
            Bundler.start()
                .put(BundleConstant.ITEM, issueModel)
                .end()
        )
        setResult(RESULT_OK, intent)
        finish()
        showMessage(R.string.success, R.string.successfully_submitted)
    }

    override fun onSuccessSubmission(issueModel: PullRequest) {
        hideProgress()
        val intent = Intent()
        intent.putExtras(
            Bundler.start()
                .put(BundleConstant.ITEM, issueModel)
                .end()
        )
        setResult(RESULT_OK, intent)
        finish()
        showMessage(R.string.success, R.string.successfully_submitted)
    }

    override fun onShowUpdate() {
        hideProgress()
        Toasty.error(App.getInstance(), getString(R.string.new_version)).show()
    }

    override fun onShowIssueMisc() {
        TransitionManager.beginDelayedTransition(findViewById(R.id.parent))
        issueMiscLayout!!.visibility =
            if (presenter!!.isCollaborator) View.VISIBLE else View.GONE
        //TODO
    }

    override fun providePresenter(): CreateIssuePresenter {
        return CreateIssuePresenter()
    }

    override fun layout(): Int {
        return R.layout.create_issue_layout
    }

    override val isTransparent: Boolean
        get() = false

    override fun canBack(): Boolean {
        return true
    }

    override val isSecured: Boolean
        get() = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        description!!.setOnTouchListener { _, event ->
            onTouch(event)
        }
        submit!!.setOnThrottleClickListener {
            onClick()
        }
        listOf(
            R.id.addAssignee, R.id.addLabels, R.id.addMilestone
        ).map { viewFind<View>(it)!! }.setOnThrottleClickListener {
            onViewClicked(it)
        }
        if (savedInstanceState == null) {
            val bundle = intent.extras
            login = bundle!!.getString(BundleConstant.EXTRA)
            repoId = bundle.getString(BundleConstant.ID)
            isFeedback = bundle.getBoolean(BundleConstant.EXTRA_TWO)
            if (bundle.getParcelable<Parcelable?>(BundleConstant.ITEM) != null) {
                if (bundle.getParcelable<Parcelable>(BundleConstant.ITEM) is Issue) {
                    issue = bundle.getParcelable(BundleConstant.ITEM)
                    setTitle(getString(R.string.update_issue))
                } else if (bundle.getParcelable<Parcelable>(BundleConstant.ITEM) is PullRequest) {
                    pullRequest = bundle.getParcelable(BundleConstant.ITEM)
                    setTitle(getString(R.string.update_pull_request))
                }
            }
            if (issue != null) {
                e(issue!!.labels, issue!!.milestone, issue!!.assignees)
                if (issue!!.labels != null) {
                    onSelectedLabels(ArrayList(issue!!.labels!!.filterNotNull()))
                }
                if (issue!!.assignees != null) {
                    onSelectedAssignees(
                        ArrayList(
                            issue!!.assignees!!.filterNotNull()
                        ), false
                    )
                }
                if (issue!!.milestone != null) {
                    milestoneModel = issue!!.milestone
                    onMilestoneSelected(milestoneModel!!)
                }
                if (!isEmpty(issue!!.title)) {
                    if (title!!.editText != null) title!!.editText!!.setText(issue!!.title)
                }
                if (!isEmpty(issue!!.body)) {
                    onSetCode(issue!!.body!!)
                }
            }
            if (pullRequest != null) {
                if (pullRequest!!.labels != null) {
                    onSelectedLabels(ArrayList(pullRequest!!.labels!!.filterNotNull()))
                }
                if (pullRequest!!.assignees != null) {
                    users.addAll(pullRequest!!.assignees!!.filterNotNull())
                    onSelectedAssignees(
                        ArrayList(
                            pullRequest!!.assignees!!.filterNotNull()
                        ), false
                    )
                }
                if (pullRequest!!.milestone != null) {
                    milestoneModel = pullRequest!!.milestone
                    onMilestoneSelected(milestoneModel!!)
                }
                if (!isEmpty(pullRequest!!.title)) {
                    if (title!!.editText != null) title!!.editText!!
                        .setText(pullRequest!!.title)
                }
                if (!isEmpty(pullRequest!!.body)) {
                    onSetCode(pullRequest!!.body!!)
                }
            }
        }
        presenter!!.checkAuthority(login!!, repoId!!)
        if (isFeedback || "HardcodedCat".equals(login, ignoreCase = true) && repoId.equals(
                "FastHub-RE",
                ignoreCase = true
            )
        ) {
            Toasty.info(
                App.getInstance(),
                getString(R.string.report_issue_warning),
                Toast.LENGTH_LONG
            ).show()
            setTitle(R.string.submit_feedback)
            presenter!!.onCheckAppVersion()
        }
        if (BuildConfig.DEBUG && isFeedback) {
            alertDialog = AlertDialog.Builder(this)
                .setTitle("You are currently using a debug build")
                .setMessage(
                    """
    Feature requests can be submitted here.
    Happy Testing
    """.trimIndent()
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        if (toolbar != null) toolbar!!.subtitle = "$login/$repoId"
        setTaskName(
            "$login/$repoId - " + if (isFeedback) getString(R.string.submit_feedback) else getString(
                R.string.create_issue
            )
        )
    }

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        AppHelper.hideKeyboard(title!!)
        presenter!!.onActivityForResult(it.resultCode, BundleConstant.REQUEST_CODE, it.data)
    }

    override fun onBackPressed() {
        if (isEmpty(title)) {
            super.onBackPressed()
        } else {
            ViewHelper.hideKeyboard(title!!)
            newInstance(
                getString(R.string.close), getString(R.string.unsaved_data_warning),
                Bundler.start().put("primary_extra", getString(R.string.discard))
                    .put("secondary_extra", getString(R.string.cancel))
                    .put(BundleConstant.EXTRA, true).end()
            ).show(supportFragmentManager, MessageDialogView.TAG)
        }
    }

    override fun onDestroy() {
        if (alertDialog != null && alertDialog!!.isShowing) {
            alertDialog!!.dismiss()
        }
        super.onDestroy()
    }

    override fun onMessageDialogActionClicked(isOk: Boolean, bundle: Bundle?) {
        super.onMessageDialogActionClicked(isOk, bundle)
        if (isOk && bundle != null) {
            finish()
        }
    }

    private fun onTouch(event: MotionEvent): Boolean {
        if (isFeedback && isEmpty(savedText)) {
            savedText = AppHelper.getFastHubIssueTemplate(isEnterprise)
        }
        if (event.action == MotionEvent.ACTION_UP) {
            val intent = Intent(this, EditorActivity::class.java)
            intent.putExtras(
                Bundler.start()
                    .put(BundleConstant.EXTRA, toString(savedText))
                    .put(BundleConstant.EXTRA_TYPE, BundleConstant.ExtraType.FOR_RESULT_EXTRA)
                    .put(BundleConstant.IS_ENTERPRISE, isEnterprise)
                    .end()
            )
            ActivityHelper.startLauncher(launcher, intent, submit!!)
            return true
        }
        return false
    }

    fun onClick() {
        presenter!!.onSubmit(
            toString(title),
            savedText,
            login!!,
            repoId!!,
            issue,
            pullRequest,
            labelModels,
            milestoneModel,
            users
        )
    }

    private fun onViewClicked(view: View) {
        when (view.id) {
            R.id.addAssignee -> AssigneesDialogFragment.newInstance(login!!, repoId!!, false)
                .show(supportFragmentManager, "AssigneesDialogFragment")
            R.id.addLabels -> {
                val labelModels = LabelListModel()
                labelModels.addAll(this.labelModels)
                LabelsDialogFragment.newInstance(labelModels, repoId!!, login!!)
                    .show(supportFragmentManager, "LabelsDialogFragment")
            }
            R.id.addMilestone -> MilestoneDialogFragment.newInstance(
                login!!, repoId!!
            )
                .show(supportFragmentManager, "MilestoneDialogFragment")
        }
    }

    override fun onSelectedLabels(labels: ArrayList<LabelModel>) {
        this.labelModels.clear()
        this.labelModels.addAll(labels)
        val builder = builder()
        for (i in labels.indices) {
            val labelModel = labels[i]
            val color = Color.parseColor("#" + labelModel.color)
            if (i > 0) {
                builder.append(" ").append(" " + labelModel.name + " ", LabelSpan(color))
            } else {
                builder.append(labelModel.name + " ", LabelSpan(color))
            }
        }
        this.labels!!.text = builder
    }

    override fun onMilestoneSelected(milestoneModel: MilestoneModel) {
        e(milestoneModel.title, milestoneModel.description, milestoneModel.number)
        this.milestoneModel = milestoneModel
        milestoneTitle!!.text = milestoneModel.title
        if (!isEmpty(milestoneModel.description)) {
            milestoneDescription!!.text = milestoneModel.description
            milestoneDescription!!.visibility = View.VISIBLE
        } else {
            milestoneDescription!!.text = ""
            milestoneDescription!!.visibility = View.GONE
        }
    }

    override fun onSelectedAssignees(users: ArrayList<User>, isAssignees: Boolean) {
        this.users.clear()
        this.users.addAll(users)
        val builder = builder()
        for (i in users.indices) {
            val user = users[i]
            builder.append(user.login)
            if (i != users.size - 1) {
                builder.append(", ")
            }
        }
        assignee!!.text = builder
    }

    companion object {
        fun startForResult(
            fragment: Fragment,
            launcher: ActivityResultLauncher<Intent>,
            login: String,
            repoId: String,
            isEnterprise: Boolean
        ) {
            val intent = Intent(fragment.context, CreateIssueActivity::class.java)
            intent.putExtras(
                Bundler.start()
                    .put(BundleConstant.EXTRA, login)
                    .put(BundleConstant.ID, repoId)
                    .put(
                        BundleConstant.EXTRA_TWO,
                        login.equals("HardcodedCat", ignoreCase = true) && repoId.equals(
                            "FastHub-RE",
                            ignoreCase = true
                        )
                    )
                    .put(BundleConstant.IS_ENTERPRISE, isEnterprise)
                    .end()
            )
            val view = if (fragment.activity != null) fragment.requireActivity()
                .findViewById<View>(R.id.fab) else null
            if (view != null) {
                ActivityHelper.startLauncher(launcher, intent, view)
            } else {
                launcher.launch(intent)
            }
        }

        fun startForResult(
            activity: Activity,
            launcher: ActivityResultLauncher<Intent>,
            login: String,
            repoId: String,
            issueModel: Issue?,
            isEnterprise: Boolean
        ) {
            if (issueModel != null) {
                val intent = Intent(activity, CreateIssueActivity::class.java)
                intent.putExtras(
                    Bundler.start()
                        .put(BundleConstant.EXTRA, login)
                        .put(BundleConstant.ID, repoId)
                        .put(BundleConstant.ITEM, issueModel)
                        .put(BundleConstant.IS_ENTERPRISE, isEnterprise)
                        .end()
                )
                val view = activity.findViewById<View>(R.id.fab)
                if (view != null) {
                    startForResult(launcher, intent, view)
                } else {
                    launcher.launch(intent)
                }
            }
        }

        fun startForResult(
            activity: Activity,
            launcher: ActivityResultLauncher<Intent>,
            login: String,
            repoId: String,
            pullRequestModel: PullRequest?,
            isEnterprise: Boolean
        ) {
            if (pullRequestModel != null) {
                val intent = Intent(activity, CreateIssueActivity::class.java)
                intent.putExtras(
                    Bundler.start()
                        .put(BundleConstant.EXTRA, login)
                        .put(BundleConstant.ID, repoId)
                        .put(BundleConstant.ITEM, pullRequestModel)
                        .put(BundleConstant.IS_ENTERPRISE, isEnterprise)
                        .end()
                )
                val view = activity.findViewById<View>(R.id.fab)
                if (view != null) {
                    startForResult(launcher, intent, view)
                } else {
                    launcher.launch(intent)
                }
            }
        }

        fun getIntent(
            context: Context,
            login: String,
            repoId: String,
            isFeedback: Boolean
        ): Intent {
            val intent = Intent(context, CreateIssueActivity::class.java)
            intent.putExtras(
                Bundler.start()
                    .put(BundleConstant.EXTRA, login)
                    .put(BundleConstant.ID, repoId)
                    .put(BundleConstant.EXTRA_TWO, isFeedback)
                    .end()
            )
            return intent
        }

        fun startForResult(activity: Activity): Intent {
            val login = "HardcodedCat" // FIXME: 23/02/2017 hardcoded
            val repoId = "FastHub-RE" // FIXME: 23/02/2017 hardcoded
            val intent = Intent(activity, CreateIssueActivity::class.java)
            intent.putExtras(
                Bundler.start()
                    .put(BundleConstant.EXTRA, login)
                    .put(BundleConstant.ID, repoId)
                    .put(BundleConstant.EXTRA_TWO, true)
                    .end()
            )
            return intent
        }

        fun startForResult(
            launcher: ActivityResultLauncher<Intent>,
            intent: Intent,
            view: View
        ) {
            ActivityHelper.startLauncher(launcher, intent, view)
        }
    }
}
