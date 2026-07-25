import './Button.css'

function Button({ variant = 'primary', className = '', ...props }) {
  const classes = `btn btn--${variant}${className ? ` ${className}` : ''}`

  return <button className={classes} {...props} />
}

export default Button
